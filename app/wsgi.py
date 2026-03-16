"""
WSGI entry point for production deployment with Gunicorn
This file is used by Gunicorn and other WSGI servers
"""

import os
import sys

# Add src directory to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'src'))

from flask_app import create_app

# Create Flask application instance
app = create_app()

if __name__ == "__main__":
    app.run()
