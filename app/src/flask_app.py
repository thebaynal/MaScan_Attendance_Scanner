"""Main Flask application for MaScan Attendance System."""

from flask import Flask
from flask_cors import CORS
from flask_session import Session
from flask_sqlalchemy import SQLAlchemy
import os
import secrets
from dotenv import load_dotenv
from config.constants import *

# Load environment variables
load_dotenv()

# Database instance
db = SQLAlchemy()

def create_app():
    """Create and configure Flask application."""
    app = Flask(__name__, 
                template_folder=os.path.join(os.path.dirname(__file__), 'templates'),
                static_folder=os.path.join(os.path.dirname(__file__), 'static'))
    
    # Environment detection
    is_production = os.getenv('FLASK_ENV', 'development').lower() == 'production'
    
    # Configuration - Generate SECRET_KEY if not provided
    secret_key = os.getenv('SECRET_KEY')
    if not secret_key:
        if is_production:
            # Generate a random secret key for production if not set (for startup)
            # NOTE: This will change on every app restart - set SECRET_KEY env var for persistent sessions
            print("⚠️  WARNING: SECRET_KEY not set. Generating temporary key. Sessions will be lost on restart.")
            print("    Set SECRET_KEY environment variable for persistent sessions.")
            secret_key = secrets.token_hex(32)
        else:
            secret_key = 'dev-secret-key-change-in-production'
    
    app.config['SECRET_KEY'] = secret_key
    app.config['ENV'] = 'production' if is_production else 'development'
    app.config['DEBUG'] = not is_production
    
    # Database configuration
    database_url = os.getenv('DATABASE_URL')
    if database_url:
        # Fix postgres:// to postgresql:// for SQLAlchemy 2.0 compatibility
        if database_url.startswith('postgres://'):
            database_url = database_url.replace('postgres://', 'postgresql://', 1)
        app.config['SQLALCHEMY_DATABASE_URI'] = database_url
    else:
        # Development fallback to SQLite
        db_path = os.path.join(os.path.dirname(__file__), '..', 'mascan_attendance.db')
        app.config['SQLALCHEMY_DATABASE_URI'] = f'sqlite:///{db_path}'
    
    app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
    
    # Session configuration - use simple cookie-based sessions
    # (more reliable than database-backed for containerized deployments)
    app.config['SESSION_TYPE'] = 'filesystem'
    app.config['PERMANENT_SESSION_LIFETIME'] = 86400  # 24 hours
    app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB max file upload
    
    # Enable CORS
    CORS(app)
    
    # Initialize database
    db.init_app(app)
    
    # Initialize Flask-Session
    Session(app)
    
    # Register blueprints
    from routes.auth_routes import auth_bp
    from routes.dashboard_routes import dashboard_bp
    from routes.event_routes import event_bp
    from routes.attendance_routes import attendance_bp
    from routes.user_routes import user_bp
    from routes.api_routes import api_bp
    from routes.qr_management_routes import qr_mgmt_bp
    
    app.register_blueprint(auth_bp)
    app.register_blueprint(dashboard_bp)
    app.register_blueprint(event_bp)
    app.register_blueprint(attendance_bp)
    app.register_blueprint(user_bp)
    app.register_blueprint(api_bp, url_prefix='/api')
    app.register_blueprint(qr_mgmt_bp)
    
    return app
