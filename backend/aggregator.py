import requests
import re
import concurrent.futures
import time

# --- KONFIGURASI SUMBER ---
SOURCES = [
    # VLESS / VMESS / TROJAN Sources (Base64 or Plain)
    "https://raw.githubusercontent.com/mrzero0nol/My-v2ray/main/proxyList.txt",
    "https://raw.githubusercontent.com/freefq/free/master/vless.txt",
    "https://raw.githubusercontent.com/rostergamer/v2ray/master/vless",
    "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub",
    "https://raw.githubusercontent.com/peasoft/NoMoreWalls/master/list.txt",
    "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/Eternity.txt",
    "https://raw.githubusercontent.com/mfuu/v2ray/master/vless",
    "https://raw.githubusercontent.com/ermaozi/get_subscribe/main/subscribe/v2ray.txt",
    "https://raw.githubusercontent.com/Barimehdi/sub_v2ray/refs/heads/main/vless.txt",
    "https://raw.githubusercontent.com/aiboboxx/v2rayfree/main/v2ray",
    "https://raw.githubusercontent.com/learnhard-cn/free_proxy_ss/main/vless/vless.txt",
    
    # SOCKS5 / HTTP Raw Lists
    "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
    "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt",
    "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
    "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/socks5.txt",
    "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.txt",
    "https://raw.githubusercontent.com/prxchk/proxy-list/main/http.txt",
    "https://raw.githubusercontent.com/prxchk/proxy-list/main/socks5.txt",
    "https://raw.githubusercontent.com/zloi-user/hideip.me/main/http.txt",
    "https://raw.githubusercontent.com/zloi-user/hideip.me/main/socks5.txt"
]

# Regex untuk menangkap IP:Port
# Format: IP (0-255) diikuti oleh : atau spasi atau koma, lalu Port (2-5 digit)
REGEX_IP_PORT = re.compile(r'(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})[:\s,\t]+(\d{2,5})')

def fetch_source(url):
    try:
        print(f"Fetching: {url}")
        resp = requests.get(url, timeout=10)
        if resp.status_code == 200:
            return resp.text
        else:
            print(f"Failed ({resp.status_code}): {url}")
            return ""
    except Exception as e:
        print(f"Error {url}: {e}")
        return ""

def extract_proxies(content):
    proxies = set()
    # 1. Coba cari link VLESS/Trojan dulu (prioritas)
    for line in content.splitlines():
        line = line.strip()
        if line.startswith("vless://") or line.startswith("trojan://") or line.startswith("vmess://"):
            # Simpan raw link-nya?
            # Untuk RedBunnyNative saat ini kita butuh IP:Port untuk di-ping
            # Jadi kita ekstrak IP:Port dari link vless juga
            match = re.search(r'@([^:]+):(\d+)', line)
            if match:
                ip, port = match.groups()
                proxies.add(f"{ip}:{port}")
    
    # 2. Cari semua pola IP:Port di teks (Global Search)
    matches = REGEX_IP_PORT.findall(content)
    for ip, port in matches:
        # Validasi range IP
        if all(0 <= int(part) <= 255 for part in ip.split('.')):
            proxies.add(f"{ip}:{port}")
            
    return proxies

def main():
    all_content = []
    
    # Parallel Fetching
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        results = executor.map(fetch_source, SOURCES)
        all_content = list(results)
    
    total_proxies = set()
    for content in all_content:
        if content:
            extracted = extract_proxies(content)
            total_proxies.update(extracted)
            
    print(f"\nTotal Unique Proxies: {len(total_proxies)}")
    
    # Simpan ke file
    with open("all_proxies.txt", "w") as f:
        for proxy in total_proxies:
            f.write(proxy + "\n")
            
    print("Saved to all_proxies.txt")

if __name__ == "__main__":
    main()
