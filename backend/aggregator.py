import requests
import re
import asyncio
import aiohttp
import time
import base64
import urllib.parse

# --- KONFIGURASI SUMBER ---
SOURCES = [
    # V2RAY / XRAY (Vless/Trojan/Vmess)
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
    
    # HTTP / SOCKS5
    "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
    "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/socks5.txt",
    "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.txt",
    "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt"
]

# Regex untuk menangkap IP:Port (Format raw)
REGEX_IP_PORT = re.compile(r'\b(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})[:\s,\t]+(\d{2,5})\b')

TARGET_URL = "http://www.speedtest.net"
CHECK_TIMEOUT = 5

def decode_base64(s):
    try:
        return base64.b64decode(s).decode('utf-8')
    except:
        return s

async def fetch_source(session, url):
    try:
        async with session.get(url, timeout=15) as response:
            if response.status == 200:
                text = await response.text()
                # Cek apakah ini Base64 murni (Subscription link)
                if not " " in text[:50] and len(text) > 20:
                    try:
                        decoded = base64.b64decode(text).decode('utf-8', errors='ignore')
                        return decoded
                    except:
                        pass
                return text
    except:
        pass
    return ""

async def check_http_proxy(proxy_str, session):
    """Cek HTTP Proxy dengan mencoba akses Speedtest"""
    # Format: IP:PORT
    proxy_url = f"http://{proxy_str}"
    try:
        start = time.time()
        async with session.get(TARGET_URL, proxy=proxy_url, timeout=CHECK_TIMEOUT, allow_redirects=True) as response:
            if response.status in [200, 301, 302]:
                return proxy_str
    except:
        pass
    return None

async def check_tcp_connect(proxy_config):
    """
    Cek VLESS/Trojan dengan TCP Connect ke IP:PORT.
    Kita tidak bisa cek UUID valid tanpa Xray Core, tapi minimal kita tahu servernya ON.
    """
    try:
        # Parsing manual vless://uuid@ip:port...
        # Atau trojan://password@ip:port...
        uri = proxy_config
        if "@" in uri and ":" in uri:
            # Ambil bagian setelah @ dan sebelum ? atau / atau #
            main_part = uri.split("@")[1].split("?")[0].split("#")[0].split("/")[0]
            if ":" in main_part:
                ip, port = main_part.split(":")
                port = int(port)
                
                # Lakukan TCP Connect
                reader, writer = await asyncio.wait_for(
                    asyncio.open_connection(ip, port), timeout=CHECK_TIMEOUT
                )
                writer.close()
                await writer.wait_closed()
                return proxy_config # Server hidup!
    except:
        pass
    return None

async def main():
    print(f"🚀 Starting Smart Aggregator...")
    
    raw_proxies = set()   # Set IP:Port
    vless_proxies = set() # Set vless://...
    
    async with aiohttp.ClientSession() as session:
        tasks = [fetch_source(session, url) for url in SOURCES]
        results = await asyncio.gather(*tasks)
        
        for content in results:
            if not content: continue
            
            # 1. Cari Link V2Ray
            for line in content.splitlines():
                line = line.strip()
                if line.startswith("vless://") or line.startswith("trojan://") or line.startswith("vmess://"):
                    vless_proxies.add(line)
            
            # 2. Cari Raw IP:Port (Hanya jika bukan baris VLESS)
            matches = REGEX_IP_PORT.findall(content)
            for ip, port in matches:
                # Filter IP lokal/invalid sederhana
                if ip.startswith("127.") or ip.startswith("192.168."): continue
                raw_proxies.add(f"{ip}:{port}")

    print(f"📥 Collected: {len(raw_proxies)} RAW proxies, {len(vless_proxies)} VLESS/Trojan configs.")

    final_active = []
    
    # --- CHECKER RAW (HTTP) ---
    sem = asyncio.Semaphore(100)
    async with aiohttp.ClientSession() as checker_session:
        async def sem_check_http(p):
            async with sem:
                return await check_http_proxy(p, checker_session)
        
        tasks = [sem_check_http(p) for p in raw_proxies]
        results = await asyncio.gather(*tasks)
        active_raw = [r for r in results if r]
        final_active.extend(active_raw)
        print(f"✅ Active HTTP/SOCKS: {len(active_raw)}")

    # --- CHECKER VLESS (TCP) ---
    # Karena jumlahnya bisa ribuan dan TCP connect butuh resources, kita batasi atau sample
    # Untuk sekarang kita cek semua tapi dengan timeout ketat
    async def sem_check_tcp(p):
        async with sem:
            return await check_tcp_connect(p)

    tasks = [sem_check_tcp(p) for p in vless_proxies]
    results = await asyncio.gather(*tasks)
    active_vless = [r for r in results if r]
    final_active.extend(active_vless)
    print(f"✅ Active VLESS/Trojan: {len(active_vless)}")

    # Save Only Active
    with open("active_proxies.txt", "w") as f:
        f.write("\n".join(final_active))
        
    # Save All (Backup)
    with open("all_proxies.txt", "w") as f:
        f.write("\n".join(raw_proxies) + "\n" + "\n".join(vless_proxies))
        
    print("💾 Saved to 'active_proxies.txt'.")

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())
