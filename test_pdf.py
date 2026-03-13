import sqlite3
import io
import sys
import os

sys.path.append(os.path.join(os.getcwd(), 'app', 'src'))
from database.db_manager import Database
from utils.pdf_export import AttendancePDFExporter

db = Database(os.path.join(os.getcwd(), 'app', 'mascan_attendance.db'))
events = db.get_all_events()

if not events:
    print("No events found!")
else:
    event = events[0]
    event_id = event[0]
    print(f"Testing export for event: {event_id}")
    
    exporter = AttendancePDFExporter(db)
    buffer = io.BytesIO()
    try:
        exporter.export_attendance(event_id, buffer)
        buffer.seek(0)
        data = buffer.read()
        print(f"PDF generated successfully, size: {len(data)} bytes")
    except Exception as e:
        print(f"FAILED: {e}")
