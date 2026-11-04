# -*- coding: utf-8 -*-
"""
SEAZEN TASK - CONG CU DONG GOI APK & PHAT HANH OTA TU DONG
Tu dong:
1. Tang versionCode & cap nhat versionName trong build.gradle.kts va TaskRepository.kt
2. Bien dich APK Release voi chu ky co dinh (seazen-signing.keystore)
3. Tai va ghi de trực tiep SZ_Task.apk len GitHub Release (tag: Sz_Task)
4. Dong bo phien ban moi len SharePoint 'POWERAPP LATEST VERSION' (Item 11 - SZ_TASK)
"""

import os
import re
import sys
import shutil
import subprocess
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = SCRIPT_DIR
APP_BUILD_GRADLE = os.path.join(PROJECT_DIR, "app", "build.gradle.kts")
TASK_REPO_FILE = os.path.join(PROJECT_DIR, "app", "src", "main", "java", "com", "seazen", "task", "data", "TaskRepository.kt")
RELEASE_APK_SOURCE = os.path.join(PROJECT_DIR, "app", "build", "outputs", "apk", "release", "app-release.apk")
ROOT_WORKSPACE = os.path.abspath(os.path.join(PROJECT_DIR, ".."))
DEST_APK_ROOT = os.path.join(ROOT_WORKSPACE, "SZ_Task.apk")
DEST_APK_LOCAL = os.path.join(PROJECT_DIR, "SZ_Task.apk")
JAVA_HOME_PATH = r"C:\Program Files\Android\Android Studio\jbr"
GITHUB_REPO = "Sang10704/SZ_Task"
GITHUB_RELEASE_TAG = "Sz_Task"


def print_header():
    print("=" * 64)
    print("     SEAZEN TASK - CONG CU DONG GOI APK & CAP NHAT OTA TU DONG")
    print("=" * 64)


def get_current_gradle_version():
    if not os.path.exists(APP_BUILD_GRADLE):
        return "1.9.2", 21
    with open(APP_BUILD_GRADLE, "r", encoding="utf-8") as f:
        content = f.read()
    vname_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    vcode_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    vname = vname_match.group(1) if vname_match else "1.9.2"
    vcode = int(vcode_match.group(1)) if vcode_match else 21
    return vname, vcode


def update_gradle_version(new_version, new_code):
    with open(APP_BUILD_GRADLE, "r", encoding="utf-8") as f:
        content = f.read()
    content = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_version}"', content)
    content = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {new_code}', content)
    with open(APP_BUILD_GRADLE, "w", encoding="utf-8", newline="\r\n") as f:
        f.write(content)


def update_repo_fallback(new_version):
    if not os.path.exists(TASK_REPO_FILE):
        return
    with open(TASK_REPO_FILE, "r", encoding="utf-8") as f:
        content = f.read()
    pattern = r'(fun getCurrentAppVersion\(\): String \{[\s\S]*?pInfo\.versionName \?:\s*")[^"]+("[\s\S]*?\?\:\s*")[^"]+("\s*\}\s*\})'
    content = re.sub(pattern, f'\\g<1>{new_version}\\g<2>{new_version}\\g<3>', content)
    with open(TASK_REPO_FILE, "w", encoding="utf-8", newline="\r\n") as f:
        f.write(content)


def get_github_token():
    try:
        proc = subprocess.run(
            ["git", "credential", "fill"],
            input="protocol=https\nhost=github.com\n\n",
            capture_output=True,
            text=True,
            timeout=10
        )
        for line in proc.stdout.splitlines():
            if line.startswith("password="):
                return line.split("=", 1)[1].strip()
    except Exception:
        pass
    return os.environ.get("GITHUB_TOKEN", "")


def upload_to_github_release(apk_path, version_name, version_code):
    import requests
    token = get_github_token()
    if not token:
        print("[GitHub Release] Khong tim thay GitHub Token trong Windows Credential Manager.")
        return False

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json"
    }
    try:
        print(f"[GitHub Release] Dang kiem tra Release tag '{GITHUB_RELEASE_TAG}' tren {GITHUB_REPO}...")
        rel_url = f"https://api.github.com/repos/{GITHUB_REPO}/releases/tags/{GITHUB_RELEASE_TAG}"
        r = requests.get(rel_url, headers=headers, timeout=20)
        if r.status_code != 200:
            print(f"[GitHub Release] Khong tim thay Release (HTTP {r.status_code}): {r.text}")
            return False

        rel_data = r.json()
        release_id = rel_data["id"]

        # Xoa asset SZ_Task.apk cu de ghi de file moi cung ten
        for asset in rel_data.get("assets", []):
            if asset.get("name") == "SZ_Task.apk":
                asset_id = asset["id"]
                print(f"[GitHub Release] Dang xoa asset SZ_Task.apk cu (ID: {asset_id})...")
                del_url = f"https://api.github.com/repos/{GITHUB_REPO}/releases/assets/{asset_id}"
                del_res = requests.delete(del_url, headers=headers, timeout=20)
                if del_res.status_code not in [200, 204]:
                    print(f"[GitHub Release] Canh bao xoa asset cu: HTTP {del_res.status_code}")

        # Tai len SZ_Task.apk moi
        print(f"[GitHub Release] Dang tai len SZ_Task.apk (v{version_name}, build {version_code})...")
        upload_url = f"https://uploads.github.com/repos/{GITHUB_REPO}/releases/{release_id}/assets?name=SZ_Task.apk"
        upload_headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/vnd.android.package-archive"
        }
        with open(apk_path, "rb") as f:
            up_res = requests.post(upload_url, headers=upload_headers, data=f, timeout=180)

        if up_res.status_code in [200, 201]:
            dl_url = up_res.json().get("browser_download_url", "")
            print(f"[GitHub Release] Thanh cong! Da ghi de SZ_Task.apk tai: {dl_url}")
            return True
        else:
            print(f"[GitHub Release] Loi tai len: HTTP {up_res.status_code} - {up_res.text}")
            return False
    except Exception as e:
        print(f"[GitHub Release] Loi: {e}")
        return False


def update_sharepoint_version(new_version):
    portal_app_dir = os.path.join(ROOT_WORKSPACE, "SEAZEN_HR_PORTAL_APP")
    if not os.path.exists(portal_app_dir):
        print("[SharePoint] Khong tim thay thu muc SEAZEN_HR_PORTAL_APP, bo qua cap nhat SharePoint.")
        return False
    try:
        if portal_app_dir not in sys.path:
            sys.path.insert(0, portal_app_dir)
        import requests
        from app.core.graph_client import SharePointCoreClient
        from app.core.config import LIST_APP_VERSION

        print("[SharePoint] Dang xac thuc Bearer Token qua App Registration...")
        client = SharePointCoreClient()
        token = client.acquire_sharepoint_token()
        headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/json;odata=verbose",
            "Content-Type": "application/json;odata=verbose"
        }
        digest_res = requests.post(
            "https://seazenvn.sharepoint.com/sites/ONESEAZEN/_api/contextinfo",
            headers={"Authorization": f"Bearer {token}", "Accept": "application/json;odata=verbose"}
        )
        digest = digest_res.json()["d"]["GetContextWebInformation"]["FormDigestValue"]
        headers["X-RequestDigest"] = digest
        headers["X-HTTP-Method"] = "MERGE"
        headers["IF-MATCH"] = "*"

        url = f"https://seazenvn.sharepoint.com/sites/ONESEAZEN/_api/web/lists(guid'{LIST_APP_VERSION}')/items(11)"
        payload = {
            "__metadata": {"type": "SP.Data.POWERAPP_x0020_LATEST_x0020_VERSIONListItem"},
            "LatestVersion": new_version
        }
        res = requests.post(url, headers=headers, json=payload)
        if res.status_code in [200, 204]:
            print(f"[SharePoint] Thanh cong! Da cap nhat muc 11 (SZ_TASK) len v{new_version} tren Cloud.")
            return True
        else:
            print(f"[SharePoint] Loi cap nhat: HTTP {res.status_code} - {res.text}")
            return False
    except Exception as e:
        print(f"[SharePoint] Loi: {e}")
        return False


def build_apk():
    print("-" * 64)
    print("Dang tien hanh bien dich APK Release qua Gradle...")
    print(f"Thu muc du an: {PROJECT_DIR}")
    print(f"JAVA_HOME: {JAVA_HOME_PATH}")
    print("-" * 64)

    env = os.environ.copy()
    env["JAVA_HOME"] = JAVA_HOME_PATH
    gradlew_cmd = os.path.join(PROJECT_DIR, "gradlew.bat")

    start_time = time.time()
    result = subprocess.run([gradlew_cmd, "assembleRelease"], cwd=PROJECT_DIR, env=env)
    duration = time.time() - start_time

    if result.returncode != 0:
        print(f"\n[THAT BAI] Qua trinh bien dich APK gap loi (ma loi: {result.returncode}).")
        return False

    if not os.path.exists(RELEASE_APK_SOURCE):
        print(f"\n[THAT BAI] Khong tim thay file APK dau ra tai: {RELEASE_APK_SOURCE}")
        return False

    file_size_mb = os.path.getsize(RELEASE_APK_SOURCE) / (1024 * 1024)

    shutil.copy2(RELEASE_APK_SOURCE, DEST_APK_ROOT)
    shutil.copy2(RELEASE_APK_SOURCE, DEST_APK_LOCAL)

    print("\n" + "=" * 64)
    print("                DONG GOI APK THANH CONG!")
    print("=" * 64)
    print(f"Thoi gian bien dich: {duration:.1f} giay")
    print(f"Dung luong APK:      {file_size_mb:.2f} MB")
    print(f"File xuat ban:       {DEST_APK_ROOT}")
    print("=" * 64)
    return True


def main():
    print_header()
    curr_ver, curr_code = get_current_gradle_version()
    print(f"Phien ban hien tai trong du an: v{curr_ver} (build code: {curr_code})")
    print()

    user_input = input(f"Nhap phien ban moi can dong goi [Nhan Enter de dung {curr_ver}]: ").strip()
    if user_input:
        target_version = user_input.lstrip("v").strip()
    else:
        target_version = curr_ver
    target_code = curr_code + 1

    print(f"\n-> Phien ban se dong goi: v{target_version} (versionCode: {target_code})")

    gh_choice = input("\nTu dong tai & ghi de SZ_Task.apk len GitHub Release 'Sz_Task'? (Y/n): ").strip().lower()
    should_upload_gh = gh_choice != "n"

    sp_choice = input("Dong bo phien ban len SharePoint 'POWERAPP LATEST VERSION' sau khi build? (Y/n): ").strip().lower()
    should_update_sp = sp_choice != "n"

    print("\nDang cap nhat thong so phien ban vao ma nguon...")
    update_gradle_version(target_version, target_code)
    update_repo_fallback(target_version)
    print("[Ma nguon] Da cap nhat build.gradle.kts va TaskRepository.kt.")

    # 1. Bien dich APK truoc
    success = build_apk()

    if success:
        # 2. Ghi de file SZ_Task.apk tren GitHub Release
        if should_upload_gh:
            upload_to_github_release(DEST_APK_ROOT, target_version, target_code)

        # 3. Cap nhat SharePoint sau khi APK da san sang tren GitHub Release
        if should_update_sp:
            update_sharepoint_version(target_version)

        open_folder = input("\nMo thu muc chua file SZ_Task.apk tren Windows Explorer? (Y/n): ").strip().lower()
        if open_folder != "n":
            subprocess.run(["explorer", f"/select,{DEST_APK_ROOT}"])

    input("\nNhan Enter de thoat...")


if __name__ == "__main__":
    main()
