"""Launch the local, disposable ManyManager design preview."""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import webbrowser


class PreviewServer(ThreadingHTTPServer):
    # Windows otherwise permits multiple HTTP servers to bind the same port.
    allow_reuse_address = False


def main():
    directory = Path(__file__).resolve().parent / "ui-reference"
    handler = partial(SimpleHTTPRequestHandler, directory=str(directory))
    try:
        server = PreviewServer(("127.0.0.1", 8000), handler)
    except OSError:
        server = PreviewServer(("127.0.0.1", 0), handler)

    with server:
        url = f"http://127.0.0.1:{server.server_port}/"
        print(f"{url}\nStop with Ctrl+C.", flush=True)
        try:
            if not webbrowser.open(url):
                print("Open the URL above in your browser.", flush=True)
        except Exception as error:
            print(f"Browser could not open ({error}). Open the URL above.", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\nPreview stopped.")


if __name__ == "__main__":
    main()
