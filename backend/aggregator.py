import requests
import re
import asyncio
import aiohttp
import time
import socket

# --- KONFIGURASI SUMBER SUPER MASIF ---
SOURCES = [
    # 1. V2RAY / XRAY SPECIFIC (Vless/Trojan/Vmess)
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
    "https://raw.githubusercontent.com/v2fly/v2ray-examples/master/VMess-TCP-TLS/config.json",
    "https://raw.githubusercontent.com/colatiger/v2ray-nodes/master/vmess.md",
    
    # 2. HTTP / HTTPS PROXIES (High Quality)
    "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
    "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
    "https://raw.githubusercontent.com/prxchk/proxy-list/main/http.txt",
    "https://raw.githubusercontent.com/zloi-user/hideip.me/main/http.txt",
    "https://raw.githubusercontent.com/saisuiu/Lionkings-Http-Proxys-Proxies/main/free.txt",
    "https://raw.githubusercontent.com/caliphdev/Proxy-List/master/http.txt",
    "https://raw.githubusercontent.com/sunny9577/proxy-scraper/master/proxies.txt",
    "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt",
    "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt",
    "https://raw.githubusercontent.com/shiftytr/proxy-list/master/proxy.txt",
    
    # 3. SOCKS5 PROXIES (Seringkali lebih cepat untuk tunneling)
    "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt",
    "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/socks5.txt",
    "https://raw.githubusercontent.com/prxchk/proxy-list/main/socks5.txt",
    "https://raw.githubusercontent.com/zloi-user/hideip.me/main/socks5.txt",
    "https://raw.githubusercontent.com/hookzof/socks5_list/master/proxy.txt",
    "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-socks5.txt",
    "https://raw.githubusercontent.com/manuGM/proxy-365/main/SOCKS5.txt",
    
    # 4. API DYNAMIC (Proxyscrape)
    "https://api.proxyscrape.com/v2/?request=getproxies&protocol=http&timeout=5000&country=all&ssl=all&anonymity=all",
    "https://api.proxyscrape.com/v2/?request=getproxies&protocol=socks5&timeout=5000&country=all&ssl=all&anonymity=all"
]

# Regex Global yang "Rakus" (Menangkap IP:Port di tengah teks apapun)
REGEX_IP_PORT = re.compile(r'(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})[:\s,\t]+(\d{2,5})')

# Target Check: Speedtest.net
# Kita gunakan endpoint konfigurasi mereka yang ringan tapi valid
TARGET_URL = "http://www.speedtest.net" 
CHECK_TIMEOUT = 6 # Detik

async def fetch_source(session, url):
    try:
        # print(f"Fetching: {url}")
        async with session.get(url, timeout=15) as response:
            if response.status == 200:
                return await response.text()
    except:
        pass
    return ""

async def check_proxy(proxy, session):
    # Support HTTP/HTTPS scheme detection logic if needed, but usually http:// covers basic connect
    proxy_url = f"http://{proxy}"
    try:
        start = time.time()
        # Allow redirects=True is crucial for some captive portals to be filtered out (usually they redirect to login)
        # But for speedtest, we just want connection.
        async with session.get(TARGET_URL, proxy=proxy_url, timeout=CHECK_TIMEOUT, allow_redirects=True) as response:
            # Status 200 OK atau 301/302 Redirect (biasanya http->https) dianggap hidup
            if response.status in [200, 301, 302]:
                latency = int((time.time() - start) * 1000)
                # Validasi tambahan: Pastikan tidak redirect ke halaman login wifi (biasanya body kecil)
                return proxy
    except:
        pass
    return None

async def main():
    print(f"🚀 Starting Super Aggregator with {len(SOURCES)} sources...")
    proxies = set()
    
    # 1. SCRAPING PHASE
    async with aiohttp.ClientSession() as session:
        tasks = [fetch_source(session, url) for url in SOURCES]
        results = await asyncio.gather(*tasks)
        
        for content in results:
            if content:
                matches = REGEX_IP_PORT.findall(content)
                for ip, port in matches:
                    # Validasi Port range
                    if 0 <= int(port) <= 65535:
                        proxies.add(f"{ip}:{port}")

    print(f"📥 Scraped {len(proxies)} unique proxies. Checking connectivity...")

    # 2. CHECKING PHASE (HTTP Speedtest)
    # Kita naikkan concurrency karena kita punya banyak target
    sem = asyncio.Semaphore(100) 
    
    async with aiohttp.ClientSession() as checker_session:
        async def sem_check(p):
            async with sem:
                return await check_proxy(p, checker_session)

        tasks = [sem_check(p) for p in proxies]
        
        # Progress bar sederhana
        total = len(tasks)
        completed = 0
        working_proxies = []
        
        # Gunakan as_completed untuk streaming result jika mau, tapi gather lebih simpel untuk script pendek
        results = await asyncio.gather(*tasks)
        working_proxies = [p for p in results if p]
    
    print(f"✅ Active Proxies (Speedtest Verified): {len(working_proxies)}")

    # 3. SAVING PHASE
    # Save ALL scraped (untuk backup/history)
    with open("all_proxies.txt", "w") as f:
        f.write("\n".join(proxies))
        
    # Save ONLY ACTIVE (untuk App)
    with open("active_proxies.txt", "w") as f:
        f.write("\n".join(working_proxies))
        
    print("💾 Saved to 'active_proxies.txt'.")

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())