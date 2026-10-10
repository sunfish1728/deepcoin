#!/usr/bin/env python3
"""鯨幣形象庫工具（形象庫格式 v1，規格見 讀卡工具/AVATAR.md §6）。

在「你自己的形象庫倉庫」根目錄執行：

  python deepcoin_avatar.py init                         建立 deepcoin-avatars.json、README，並幫 GitHub 倉庫加上 topic
  python deepcoin_avatar.py check model.vrm              檢查 VRM：版本、名稱、作者、授權、是否允許再配布
  python deepcoin_avatar.py pack model.vrm --char-id whale --name 鯨魚娘 --version 1.0.0
                                                         打包成角色包 zip、抽出縮圖、更新索引
  python deepcoin_avatar.py publish whale                把該角色上傳到 GitHub Releases，並提交、推送索引

只用 Python 標準函式庫；publish 需要已登入的 GitHub CLI（gh）與 git。
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import zipfile

INDEX = "deepcoin-avatars.json"
FORMAT = "deepcoin-avatars/1"
TOPIC = "deepcoin-avatar"
CHAR_ID = re.compile(r"^[A-Za-z0-9_.-]{1,64}$")
VERSION = re.compile(r"^[0-9]+(\.[0-9]+){0,2}([-+][A-Za-z0-9.]+)?$")
MAX_SIZE = 300 * 1024 * 1024  # App 端的下載上限
REPO_FILE_LIMIT = 50 * 1024 * 1024  # 直接放進 git 的建議上限（超過請用 Releases）


def die(msg):
    print("錯誤：" + msg, file=sys.stderr)
    sys.exit(1)


# ---------------- VRM 解析 ----------------

def read_glb(path):
    with open(path, "rb") as f:
        head = f.read(12)
        if len(head) < 12 or head[:4] != b"glTF":
            die(f"{path} 不是 GLB / VRM 檔")
        _, version, length = struct.unpack("<4sII", head)
        if version != 2:
            die("只支援 glTF 2.0")
        n, kind = struct.unpack("<II", f.read(8))
        if kind != 0x4E4F534A:
            die("GLB 第一個區塊不是 JSON")
        gltf = json.loads(f.read(n).decode("utf-8"))
        binary = b""
        rest = f.read(8)
        if len(rest) == 8:
            n2, kind2 = struct.unpack("<II", rest)
            if kind2 == 0x004E4942:
                binary = f.read(n2)
    return gltf, binary


def vrm_info(path):
    """回傳 dict：version, name, authors, license, redistribution(True/False/None), commercial, thumb(bytes|None)"""
    gltf, binary = read_glb(path)
    ext = gltf.get("extensions", {})
    info = {"thumb": None}
    image_index = None
    if "VRMC_vrm" in ext:
        meta = ext["VRMC_vrm"].get("meta", {})
        info.update(
            version="1.0",
            name=meta.get("name", ""),
            authors=meta.get("authors", []),
            license=meta.get("licenseUrl", ""),
            redistribution=meta.get("allowRedistribution"),
            commercial=meta.get("commercialUsage", ""),
            modification=meta.get("modification", ""),
        )
        image_index = meta.get("thumbnailImage")
    elif "VRM" in ext:
        meta = ext["VRM"].get("meta", {})
        lic = meta.get("licenseName", "")
        info.update(
            version="0.x",
            name=meta.get("title", ""),
            authors=[a for a in [meta.get("author", "")] if a],
            license=lic + (f"（{meta.get('otherLicenseUrl')}）" if meta.get("otherLicenseUrl") else ""),
            # VRM 0.x：Redistribution_Prohibited 禁止；Other 要看作者另外的條款，視為未知
            redistribution=False if lic == "Redistribution_Prohibited" else (None if lic in ("", "Other") else True),
            commercial=meta.get("commercialUssageName", ""),
            modification="",
        )
        tex = meta.get("texture")
        if isinstance(tex, int) and 0 <= tex < len(gltf.get("textures", [])):
            image_index = gltf["textures"][tex].get("source")
    else:
        die("檔案裡沒有 VRM 擴充（不是 VRM 0.x / 1.0）")
    images = gltf.get("images", [])
    if isinstance(image_index, int) and 0 <= image_index < len(images):
        bv = images[image_index].get("bufferView")
        if isinstance(bv, int):
            v = gltf["bufferViews"][bv]
            start = v.get("byteOffset", 0)
            info["thumb"] = binary[start:start + v["byteLength"]]
    return info


def print_info(info):
    red = {True: "允許", False: "禁止", None: "未註明（請確認作者條款）"}[info["redistribution"]]
    print(f"VRM {info['version']}　名稱：{info['name'] or '（無）'}")
    print(f"作者：{'、'.join(info['authors']) or '（無）'}")
    print(f"授權：{info['license'] or '（無）'}　再配布：{red}　商用：{info['commercial'] or '（無）'}")
    print(f"縮圖：{'有' if info['thumb'] else '沒有'}")


# ---------------- 索引 ----------------

def load_index():
    if not os.path.exists(INDEX):
        die(f"找不到 {INDEX}，請先在形象庫倉庫根目錄執行 init")
    with open(INDEX, encoding="utf-8") as f:
        idx = json.load(f)
    if not str(idx.get("format", "")).startswith(FORMAT):
        die(f"{INDEX} 的 format 不是 {FORMAT}")
    idx.setdefault("avatars", [])
    return idx


def save_index(idx):
    with open(INDEX, "w", encoding="utf-8", newline="\n") as f:
        json.dump(idx, f, ensure_ascii=False, indent=2)
        f.write("\n")


def git_repo_slug():
    """從 git remote 推出 owner/repo"""
    try:
        url = subprocess.run(["git", "remote", "get-url", "origin"], capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        return None
    m = re.search(r"github\.com[:/]([^/]+)/([^/]+?)(\.git)?$", url)
    return f"{m.group(1)}/{m.group(2)}" if m else None


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


# ---------------- 指令 ----------------

def cmd_init(a):
    if os.path.exists(INDEX):
        print(f"{INDEX} 已存在，略過")
    else:
        save_index({"format": FORMAT, "name": a.name or os.path.basename(os.getcwd()), "description": a.description or "", "avatars": []})
        print(f"已建立 {INDEX}")
    here = os.path.dirname(os.path.abspath(__file__))
    tpl = os.path.join(here, "template", "README.md")
    if not os.path.exists("README.md") and os.path.exists(tpl):
        shutil.copy(tpl, "README.md")
        print("已建立 README.md（請改成你的形象庫介紹）")
    slug = a.repo or git_repo_slug()
    if slug and shutil.which("gh"):
        r = subprocess.run(["gh", "repo", "edit", slug, "--add-topic", TOPIC])
        if r.returncode == 0:
            print(f"已為 {slug} 加上 topic「{TOPIC}」，App 的線上形象庫就搜得到")
    else:
        print(f"記得到 GitHub 倉庫頁面把 topic 設成「{TOPIC}」，App 的線上形象庫才搜得到")


def cmd_check(a):
    info = vrm_info(a.model)
    print_info(info)
    size = os.path.getsize(a.model)
    print(f"大小：{size / 1048576:.1f} MB")
    if info["redistribution"] is not True:
        print("⚠ 這個模型不能（或無法確定能）放到公開倉庫。")
        sys.exit(2)


def cmd_pack(a):
    if not CHAR_ID.match(a.char_id):
        die("char_id 只能用英數字、底線、點、減號，最多 64 字")
    if not VERSION.match(a.version):
        die("version 請用 1.0.0 這種格式")
    info = vrm_info(a.model)
    print_info(info)
    if info["redistribution"] is not True and not a.private:
        die("模型授權不允許（或沒註明允許）再配布，不能放到公開形象庫。只想在自己的私人環境測試請加 --private")
    if a.private:
        print("⚠ --private：略過授權檢查。這個角色包只能自己測試用，不要上傳到公開倉庫。")

    manifest = {}
    if a.avatar_json:
        with open(a.avatar_json, encoding="utf-8") as f:
            manifest = json.load(f)
    manifest.update({"v": 1, "name": a.name, "char_id": a.char_id, "model": "model.vrm"})
    if "credit" not in manifest:
        manifest["credit"] = "、".join(info["authors"])

    thumb = None
    if a.thumb:
        with open(a.thumb, "rb") as f:
            thumb = f.read()
    elif info["thumb"]:
        thumb = info["thumb"]
    ext = ".jpg" if thumb and thumb[:2] == b"\xff\xd8" else ".png"
    if thumb:
        manifest["thumb"] = "thumb" + ext

    os.makedirs("dist", exist_ok=True)
    tag = f"{a.char_id}-v{a.version}"
    zpath = os.path.join("dist", tag + ".zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(a.model, "model.vrm")
        z.writestr("avatar.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        if thumb:
            z.writestr("thumb" + ext, thumb)
    size = os.path.getsize(zpath)
    if size > MAX_SIZE:
        die(f"角色包 {size / 1048576:.0f} MB，超過 App 的 300 MB 上限")

    # 縮圖另存一份在倉庫裡，給 App 的線上形象庫顯示
    thumb_rel = ""
    if thumb:
        os.makedirs("thumbs", exist_ok=True)
        thumb_rel = f"thumbs/{a.char_id}{ext}"
        with open(thumb_rel, "wb") as f:
            f.write(thumb)

    idx = load_index()
    if a.host == "repo":
        if size > REPO_FILE_LIMIT:
            die("超過 50 MB 的檔案請用 --host releases（GitHub 不建議把大檔放進 git，LFS 檔案 App 也抓不到）")
        os.makedirs("files", exist_ok=True)
        shutil.copy(zpath, os.path.join("files", tag + ".zip"))
        file_url = f"files/{tag}.zip"
    else:
        slug = a.repo or git_repo_slug()
        if not slug:
            die("找不到 GitHub 倉庫名稱，請加 --repo owner/repo")
        file_url = f"https://github.com/{slug}/releases/download/{tag}/{tag}.zip"

    entry = {
        "char_id": a.char_id,
        "name": a.name,
        "version": a.version,
        "file": file_url,
        "sha256": sha256(zpath),
        "size": size,
        "thumb": thumb_rel,
        "vrm": info["version"],
        "authors": info["authors"],
        "license": a.license or info["license"],
        "description": a.description or "",
    }
    if not thumb_rel:
        del entry["thumb"]
    idx["avatars"] = [e for e in idx["avatars"] if e.get("char_id") != a.char_id] + [entry]
    save_index(idx)
    print(f"\n已打包：{zpath}（{size / 1048576:.1f} MB）")
    print(f"已更新 {INDEX}：{a.char_id} v{a.version}")
    if a.host == "releases":
        print(f"下一步：python {os.path.basename(__file__)} publish {a.char_id}")
    else:
        print("下一步：git add -A && git commit && git push")


def cmd_publish(a):
    idx = load_index()
    entry = next((e for e in idx["avatars"] if e.get("char_id") == a.char_id), None)
    if not entry:
        die(f"{INDEX} 裡沒有 {a.char_id}，請先 pack")
    tag = f"{a.char_id}-v{entry['version']}"
    zpath = os.path.join("dist", tag + ".zip")
    if not os.path.exists(zpath):
        die(f"找不到 {zpath}，請先 pack")
    if sha256(zpath) != entry.get("sha256"):
        die("dist 裡的角色包和索引的 SHA-256 不符，請重新 pack")
    if not shutil.which("gh"):
        die("需要 GitHub CLI（gh），並先執行 gh auth login")
    run = lambda *cmd: subprocess.run(cmd, check=True)
    # 一個版本一個 tag，下載網址永遠不變，App 端的快取才不會錯
    run("gh", "release", "create", tag, zpath, "--title", f"{entry['name']} v{entry['version']}",
        "--notes", f"鯨幣角色包 {a.char_id} v{entry['version']}\nSHA-256：{entry['sha256']}")
    paths = [INDEX] + (["thumbs"] if os.path.isdir("thumbs") else [])
    run("git", "add", *paths)
    run("git", "commit", "-m", f"新增角色 {a.char_id} v{entry['version']}")
    run("git", "push")
    print(f"\n完成。寫卡時「形象 URL」填倉庫網址、「角色 ID」填 {a.char_id} 即可。")


def main():
    p = argparse.ArgumentParser(description="鯨幣形象庫工具")
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("init", help="建立形象庫")
    s.add_argument("--name", help="形象庫名稱")
    s.add_argument("--description", help="形象庫說明")
    s.add_argument("--repo", help="GitHub owner/repo（預設從 git remote 推出）")
    s.set_defaults(fn=cmd_init)

    s = sub.add_parser("check", help="檢查 VRM")
    s.add_argument("model")
    s.set_defaults(fn=cmd_check)

    s = sub.add_parser("pack", help="打包角色並更新索引")
    s.add_argument("model", help=".vrm 檔")
    s.add_argument("--char-id", required=True)
    s.add_argument("--name", required=True, help="角色顯示名稱")
    s.add_argument("--version", required=True, help="例如 1.0.0；每次更新模型都要換新版本")
    s.add_argument("--avatar-json", help="自訂的 avatar.json（表情、台詞、鏡頭等，見 AVATAR.md §3）")
    s.add_argument("--thumb", help="縮圖 PNG（預設從 VRM 抽出）")
    s.add_argument("--license", help="授權說明（預設取 VRM 內的授權）")
    s.add_argument("--description", help="角色介紹")
    s.add_argument("--host", choices=["releases", "repo"], default="releases",
                   help="releases：放 GitHub Releases（預設，建議）；repo：直接放進倉庫 files/（50 MB 以下）")
    s.add_argument("--repo", help="GitHub owner/repo（預設從 git remote 推出）")
    s.add_argument("--private", action="store_true", help="略過授權檢查（只限自己測試，不可公開）")
    s.set_defaults(fn=cmd_pack)

    s = sub.add_parser("publish", help="上傳到 GitHub Releases 並推送索引")
    s.add_argument("char_id")
    s.set_defaults(fn=cmd_publish)

    a = p.parse_args()
    a.fn(a)


if __name__ == "__main__":
    main()
