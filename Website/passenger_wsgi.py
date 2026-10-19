"""Entry point for cPanel's Setup Python App (Phusion Passenger).

In cPanel set "Application startup file" to passenger_wsgi.py and
"Application Entry point" to application.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from aerborne import create_app  # noqa: E402

application = create_app()
