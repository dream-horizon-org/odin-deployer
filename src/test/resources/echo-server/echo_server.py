#!/usr/bin/env python3
"""
Interceptor server that modifies component payloads for testing.
Used for integration testing of InterceptorService.
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import sys

class InterceptorHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        # Read the request body
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length)

        # Log the request with details
        print("="*80, file=sys.stderr)
        print(f"[INTERCEPTOR] Received POST request to {self.path}", file=sys.stderr)
        print(f"[INTERCEPTOR] Headers:", file=sys.stderr)
        for header, value in self.headers.items():
            print(f"  {header}: {value}", file=sys.stderr)
        print(f"[INTERCEPTOR] Body Length: {content_length} bytes", file=sys.stderr)
        print(f"[INTERCEPTOR] Original Body:", file=sys.stderr)
        print(body.decode('utf-8', errors='ignore'), file=sys.stderr)
        print("="*80, file=sys.stderr)

        try:
            # Parse the JSON payload
            payload = json.loads(body.decode('utf-8'))

            # Modify the payload like a real interceptor would
            modified_payload = self.modify_payload(payload)

            # Serialize back to JSON
            modified_body = json.dumps(modified_payload).encode('utf-8')

            print(f"[INTERCEPTOR] Modified Body:", file=sys.stderr)
            print(modified_body.decode('utf-8'), file=sys.stderr)
            print("="*80, file=sys.stderr)

            # Return the modified body
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(modified_body)))
            self.end_headers()
            self.wfile.write(modified_body)

        except Exception as e:
            print(f"[INTERCEPTOR] Error processing request: {e}", file=sys.stderr)
            error_response = json.dumps({"error": str(e)}).encode('utf-8')
            self.send_response(500)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(error_response)))
            self.end_headers()
            self.wfile.write(error_response)

    def modify_payload(self, payload):
        """
        Modify the interceptor payload to simulate real interceptor behavior.
        This simulates an interceptor that:
        1. Adds a marker to identify interceptor processing
        2. Modifies component configuration values
        3. Updates provisioning parameters
        """
        # Add interceptor marker to definition config
        if 'definition' in payload and 'config' in payload['definition']:
            payload['definition']['config']['interceptor_processed'] = 'true'
            payload['definition']['config']['interceptor_timestamp'] = '2025-10-22T00:00:00Z'

            # Modify an existing config value if it exists (testConfig is the actual field name)
            if 'testConfig' in payload['definition']['config']:
                original_value = payload['definition']['config']['testConfig']
                payload['definition']['config']['testConfig'] = f"{original_value}-intercepted"

        # Modify provisioning parameters
        if 'provisioning' in payload and 'params' in payload['provisioning']:
            payload['provisioning']['params']['interceptor_modified'] = 'true'

            # Modify an existing parameter if it exists (testParam is the actual field name)
            if 'testParam' in payload['provisioning']['params']:
                original_param = payload['provisioning']['params']['testParam']
                payload['provisioning']['params']['testParam'] = f"{original_param}-modified"

        # Add interceptor metadata to the context if it exists
        if 'interceptorContext' in payload:
            if 'stage' not in payload['interceptorContext']:
                payload['interceptorContext']['stage'] = {}
            if 'config' not in payload['interceptorContext']['stage']:
                payload['interceptorContext']['stage']['config'] = {}
            payload['interceptorContext']['stage']['config']['interceptor_pass'] = 'completed'

        return payload

    def log_message(self, format, *args):
        # Custom logging to stderr
        sys.stderr.write(f"{self.address_string()} - {format % args}\n")

def run(port=5050):
    server_address = ('', port)
    httpd = HTTPServer(server_address, InterceptorHandler)
    print(f"Interceptor server running on port {port}", file=sys.stderr)
    httpd.serve_forever()

if __name__ == '__main__':
    run()
