import requests
import re
import asyncio
import aiohttp
import time
import socket

# --- KONFIGURASI SUMBER ---
SOURCES = [
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

REGEX_IP_PORT = re.compile(r'(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})[:\s,\t]+(\d{2,5})')

async def fetch_source(session, url):
    try:
        async with session.get(url, timeout=15) as response:
            if response.status == 200:
                return await response.text()
    except:
        pass
    return ""

async def check_proxy(proxy):
    ip, port = proxy.split(':')
    try:
        # TCP Connect Ping (Timeout 3s)
        reader, writer = await asyncio.wait_for(asyncio.open_connection(ip, int(port)), timeout=3)
        writer.close()
        await writer.wait_closed()
        return proxy # Return proxy if success
    except:
        return None # Return None if failed

async def main():
    print("Starting Scraper...")
    proxies = set()
    
    # 1. SCRAPING
    async with aiohttp.ClientSession() as session:
        tasks = [fetch_source(session, url) for url in SOURCES]
        results = await asyncio.gather(*tasks)
        
        for content in results:
            if content:
                # Extract IP:Port
                matches = REGEX_IP_PORT.findall(content)
                for ip, port in matches:
                    if 0 <= int(port) <= 65535:
                        proxies.add(f"{ip}:{port}")

    print(f"Scraped {len(proxies)} unique proxies. Checking vitality...")

    # 2. CHECKING (TCP PING)
    # Limit concurrency to avoid OOM or banning
    sem = asyncio.Semaphore(200) # 200 concurrent checks
    
    async def sem_check(p):
        async with sem:
            return await check_proxy(p)

    tasks = [sem_check(p) for p in proxies]
    # Use tqdm-like progress or just simple print
    results = await asyncio.gather(*tasks)
    
    working_proxies = [p for p in results if p]
    
    print(f"Alive Proxies: {len(working_proxies)}")

    # 3. SAVING
    # Save all scraped (for history) and working (for app)
    with open("all_proxies.txt", "w") as f:
        f.write("\n".join(proxies))
        
    with open("active_proxies.txt", "w") as f:
        f.write("\n".join(working_proxies))
        
    print("Saved to 'active_proxies.txt'.")

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())