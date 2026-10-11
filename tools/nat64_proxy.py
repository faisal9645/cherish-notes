import socket
import threading
import sys

DNS_MAP = {
    'github.com': '64:ff9b::8c52:7903',
    'api.github.com': '64:ff9b::8c52:7906',
    'uploads.github.com': '64:ff9b::8c52:790e',
    'raw.githubusercontent.com': '64:ff9b::b9ef:441c',
    'objects.githubusercontent.com': '64:ff9b::b9ef:441c',
}

def forward(src, dst):
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except Exception:
        pass
    finally:
        try:
            src.close()
        except Exception:
            pass
        try:
            dst.close()
        except Exception:
            pass

def handle_client(client_socket):
    try:
        request_line = b""
        while b"\r\n" not in request_line:
            chunk = client_socket.recv(1024)
            if not chunk:
                client_socket.close()
                return
            request_line += chunk
        
        # Read the rest of headers
        while b"\r\n\r\n" not in request_line:
            chunk = client_socket.recv(1024)
            if not chunk:
                break
            request_line += chunk

        first_line = request_line.split(b"\r\n")[0].decode('utf-8', errors='ignore')
        parts = first_line.split()
        if len(parts) < 2 or parts[0].upper() != 'CONNECT':
            client_socket.sendall(b"HTTP/1.1 405 Method Not Allowed\r\n\r\n")
            client_socket.close()
            return
        
        target = parts[1]
        if ':' in target:
            host, port_str = target.split(':', 1)
            port = int(port_str)
        else:
            host = target
            port = 443

        dest_ip = DNS_MAP.get(host)
        if not dest_ip:
            # Fallback: try socket.getaddrinfo for IPv6
            try:
                res = socket.getaddrinfo(host, port, socket.AF_INET6)
                dest_ip = res[0][4][0]
            except Exception:
                client_socket.sendall(b"HTTP/1.1 502 Bad Gateway\r\n\r\n")
                client_socket.close()
                return

        remote_sock = socket.socket(socket.AF_INET6, socket.SOCK_STREAM)
        remote_sock.connect((dest_ip, port))
        client_socket.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")

        t1 = threading.Thread(target=forward, args=(client_socket, remote_sock), daemon=True)
        t2 = threading.Thread(target=forward, args=(remote_sock, client_socket), daemon=True)
        t1.start()
        t2.start()
    except Exception as e:
        try:
            client_socket.close()
        except Exception:
            pass

def main():
    port = 8899
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('127.0.0.1', port))
    server.listen(50)
    print(f"NAT64 proxy running on 127.0.0.1:{port}", flush=True)

    while True:
        client, addr = server.accept()
        t = threading.Thread(target=handle_client, args=(client,), daemon=True)
        t.start()

if __name__ == '__main__':
    main()
