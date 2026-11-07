"""Synthetic, container-only metrics and notification receiver for monitoring smoke."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock

state = {"down": False, "warn": False, "token": "monitoring-smoke-token"}
events = []
lock = Lock()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def respond(self, code, body, content_type="application/json"):
        data = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        with lock:
            current = dict(state)
            received = list(events)
        if self.path == "/events":
            return self.respond(200, json.dumps(received))
        if self.path != "/actuator/prometheus":
            return self.respond(404, "{}")
        if self.headers.get("Authorization") != "Bearer " + current["token"]:
            return self.respond(401, "{}")
        if current["down"]:
            return self.respond(503, "{}")
        warn = int(current["warn"])
        lines = [f"erp_business_health_overall_status {warn}"]
        counts = {"OPEN_PERIOD_COUNT": 1, "IMPORT_FAILED_RECENT": warn,
                  "READINESS_UNPASSED_P0_P1": 0, "NEGATIVE_INVENTORY_BALANCE": 0}
        for check, count in counts.items():
            lines.append(f'erp_business_health_check_count{{check="{check}"}} {count}')
            bad = int(check == "IMPORT_FAILED_RECENT" and warn)
            lines.append(f'erp_business_health_check_status{{check="{check}"}} {bad}')
        self.respond(200, "\n".join(lines) + "\n", "text/plain; version=0.0.4")

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
        with lock:
            if self.path == "/state":
                state.update({k: v for k, v in body.items() if k in state})
            elif self.path in ("/critical", "/warning"):
                events.append({"receiver": self.path, "payload": body})
            else:
                return self.respond(404, "{}")
        self.respond(200, "{}")


ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
