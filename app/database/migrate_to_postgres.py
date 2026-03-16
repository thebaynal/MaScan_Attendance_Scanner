#!/usr/bin/env python3
"""
Migration script to move data from SQLite to PostgreSQL
Run this after setting up PostgreSQL in production
"""

import sqlite3
import psycopg2
from psycopg2 import sql
import os
import sys
from datetime import datetime

def migrate_database(sqlite_path, postgres_url):
    """
    Migrate all data from SQLite database to PostgreSQL
    
    Args:
        sqlite_path: Path to the SQLite database file
        postgres_url: PostgreSQL connection string (postgresql://user:password@host:port/dbname)
    """
    
    print("=" * 70)
    print("DATABASE MIGRATION: SQLite → PostgreSQL")
    print("=" * 70)
    print(f"Source: {sqlite_path}")
    print(f"Destination: {postgres_url.split('@')[1] if '@' in postgres_url else 'unknown'}")
    print("=" * 70)
    
    # Verify SQLite exists
    if not os.path.exists(sqlite_path):
        print(f"❌ ERROR: SQLite file not found at {sqlite_path}")
        return False
    
    try:
        # Connect to SQLite
        print("\n1. Connecting to SQLite database...")
        sqlite_conn = sqlite3.connect(sqlite_path)
        sqlite_conn.row_factory = sqlite3.Row
        sqlite_cursor = sqlite_conn.cursor()
        print("✓ Connected to SQLite")
        
        # Connect to PostgreSQL
        print("2. Connecting to PostgreSQL database...")
        postgres_conn = psycopg2.connect(postgres_url)
        postgres_cursor = postgres_conn.cursor()
        print("✓ Connected to PostgreSQL")
        
        # Check tables to migrate
        sqlite_cursor.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
        )
        tables = [row[0] for row in sqlite_cursor.fetchall()]
        print(f"\n3. Found {len(tables)} tables to migrate:")
        for table in tables:
            print(f"   - {table}")
        
        # Migrate each table
        print("\n4. Starting data migration...")
        total_rows = 0
        
        for table in tables:
            # Get column info
            sqlite_cursor.execute(f"PRAGMA table_info({table})")
            columns_info = sqlite_cursor.fetchall()
            columns = [col[1] for col in columns_info]
            
            # Get row count
            sqlite_cursor.execute(f"SELECT COUNT(*) FROM {table}")
            row_count = sqlite_cursor.fetchone()[0]
            total_rows += row_count
            
            if row_count == 0:
                print(f"   ⊘ {table}: 0 rows (skip)")
                continue
            
            # Get all data
            sqlite_cursor.execute(f"SELECT * FROM {table}")
            rows = sqlite_cursor.fetchall()
            
            if rows:
                # Prepare insert statement
                placeholders = ','.join(['%s'] * len(columns))
                insert_sql = sql.SQL("INSERT INTO {} ({}) VALUES ({})").format(
                    sql.Identifier(table),
                    sql.SQL(',').join(map(sql.Identifier, columns)),
                    sql.SQL(placeholders)
                )
                
                try:
                    # Insert rows in batches
                    for row in rows:
                        values = [val for val in row]
                        postgres_cursor.execute(insert_sql, values)
                    
                    postgres_conn.commit()
                    print(f"   ✓ {table}: {row_count} rows migrated")
                    
                except psycopg2.Error as e:
                    postgres_conn.rollback()
                    print(f"   ❌ {table}: ERROR - {e}")
                    return False
        
        # Migration summary
        print(f"\n5. Migration Summary")
        print(f"   Total rows migrated: {total_rows}")
        print("   ✓ All data successfully migrated!")
        
        # Clean up
        sqlite_conn.close()
        postgres_conn.close()
        
        print("\n" + "=" * 70)
        print("✓ MIGRATION COMPLETED SUCCESSFULLY")
        print("=" * 70)
        return True
        
    except sqlite3.Error as e:
        print(f"❌ SQLite Error: {e}")
        return False
    except psycopg2.Error as e:
        print(f"❌ PostgreSQL Error: {e}")
        return False
    except Exception as e:
        print(f"❌ Unexpected Error: {e}")
        return False


def backup_sqlite(sqlite_path):
    """Create a backup of the SQLite database before migration"""
    import shutil
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = f"{sqlite_path}.backup_{timestamp}"
    try:
        shutil.copy2(sqlite_path, backup_path)
        print(f"✓ Backup created: {backup_path}")
        return backup_path
    except Exception as e:
        print(f"⚠ Warning: Could not create backup: {e}")
        return None


if __name__ == '__main__':
    # Get configuration from environment
    sqlite_db = os.getenv('SQLITE_DB_PATH', 'mascan_attendance.db')
    postgres_url = os.getenv('DATABASE_URL', 'postgresql://user:password@localhost:5432/mascan_attendance')
    
    # If DATABASE_URL uses postgres://, convert to postgresql://
    if postgres_url.startswith('postgres://'):
        postgres_url = postgres_url.replace('postgres://', 'postgresql://', 1)
    
    print("\n📦 DATABASE MIGRATION UTILITY")
    print(f"SQLite Path: {sqlite_db}")
    print(f"PostgreSQL URL: {postgres_url.split('@')[1] if '@' in postgres_url else 'unknown'}\n")
    
    # Safety check
    response = input("⚠️  This will migrate data from SQLite to PostgreSQL. Continue? (yes/no): ").strip().lower()
    if response != 'yes':
        print("Migration cancelled.")
        sys.exit(0)
    
    # Create backup
    backup_path = backup_sqlite(sqlite_db)
    
    # Run migration
    success = migrate_database(sqlite_db, postgres_url)
    
    if success:
        print("\n💡 Next Steps:")
        print("   1. Verify data in PostgreSQL: SELECT COUNT(*) FROM [table_name];")
        print("   2. Test the application with PostgreSQL")
        print("   3. Once confirmed, the old SQLite database can be removed")
        if backup_path:
            print(f"   4. Backup saved at: {backup_path}")
        sys.exit(0)
    else:
        print("\n❌ Migration failed. Please check the errors above.")
        print(f"   Backup preserved at: {backup_path}")
        sys.exit(1)
