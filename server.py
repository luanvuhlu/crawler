#!/usr/bin/env python3
import json
import os
import re
import sys
import argparse
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs, unquote

class CrawlerDataHandler(BaseHTTPRequestHandler):
    def __init__(self, *args, data_dir=None, **kwargs):
        self.data_dir = data_dir or 'data'
        super().__init__(*args, **kwargs)

    def do_GET(self):
        parsed_path = urlparse(self.path)
        path = parsed_path.path
        query_params = parse_qs(parsed_path.query)
        
        if path == '/' or path == '/list':
            self.serve_list_page()
        elif path == '/detail':
            item_id = query_params.get('id', [None])[0]
            if item_id:
                self.serve_detail_page(item_id)
            else:
                self.send_error(404, "Item ID required")
        elif path.startswith('/images/') or path.startswith('/upload/'):
            self.serve_image(path)
        else:
            self.send_error(404, "Page not found")
    
    def serve_list_page(self):
        items = self.load_list_items()
        html_content = self.generate_list_html(items)
        self.send_response(200)
        self.send_header('Content-type', 'text/html; charset=utf-8')
        self.end_headers()
        self.wfile.write(html_content.encode('utf-8'))
    
    def serve_detail_page(self, item_id):
        list_item = self.load_list_item(item_id)
        detail_item = self.load_detail_item(item_id)
        
        if not list_item and not detail_item:
            self.send_error(404, "Item not found")
            return
            
        html_content = self.generate_detail_html(list_item, detail_item, item_id)
        self.send_response(200)
        self.send_header('Content-type', 'text/html; charset=utf-8')
        self.end_headers()
        self.wfile.write(html_content.encode('utf-8'))
    
    def serve_image(self, path):
        # Remove leading slash and decode URL before serving from data directory
        decoded_path = unquote(path[1:])
        file_path = os.path.join(self.data_dir, decoded_path)
        print(f"Serving image: {file_path}")
        if os.path.exists(file_path):
            self.send_response(200)
            if file_path.endswith('.jpg') or file_path.endswith('.jpeg'):
                self.send_header('Content-type', 'image/jpeg')
            elif file_path.endswith('.png'):
                self.send_header('Content-type', 'image/png')
            else:
                self.send_header('Content-type', 'application/octet-stream')
            self.end_headers()
            with open(file_path, 'rb') as f:
                self.wfile.write(f.read())
        else:
            self.send_error(404, "Image not found")
    
    def load_list_items(self):
        items = []
        if not os.path.exists(self.data_dir):
            return items
            
        for filename in os.listdir(self.data_dir):
            if filename.startswith('list_productlist_') and filename.endswith('.json'):
                try:
                    with open(os.path.join(self.data_dir, filename), 'r', encoding='utf-8') as f:
                        item = json.load(f)
                        # Extract ID from filename as backup
                        item_id = filename.replace('list_productlist_', '').replace('.json', '')
                        if 'ID' not in item:
                            item['_id'] = item_id
                        items.append(item)
                except Exception as e:
                    print(f"Error loading {filename}: {e}")
        return items
    
    def load_list_item(self, item_id):
        filename = os.path.join(self.data_dir, f'list_productlist_{item_id}.json')
        if os.path.exists(filename):
            try:
                with open(filename, 'r', encoding='utf-8') as f:
                    return json.load(f)
            except Exception as e:
                print(f"Error loading list item {item_id}: {e}")
        return None
    
    def load_detail_item(self, item_id):
        filename = os.path.join(self.data_dir, f'detail_productdetail_{item_id}.json')
        if os.path.exists(filename):
            try:
                with open(filename, 'r', encoding='utf-8') as f:
                    return json.load(f)
            except Exception as e:
                print(f"Error loading detail item {item_id}: {e}")
        return None
    
    def format_price(self, price):
        if not price:
            return ""
        try:
            # Remove any non-digit characters and convert to int
            price_num = int(re.sub(r'\D', '', str(price)))
            return f"{price_num:,} VND".replace(',', '.')
        except:
            return str(price)
    
    def format_boolean(self, value):
        if isinstance(value, bool):
            return "✅" if value else "❌"
        return str(value)
    
    def format_html_content(self, text):
        if not text:
            return ""
        # Simply return the HTML content as-is since it's already formatted
        return str(text)
    
    def format_image(self, image_path):
        if not image_path:
            return ""
        return f'<img src="/{image_path}" alt="Product Image" style="max-width: 200px; height: auto; margin: 5px;">'
    
    def format_images(self, images):
        if not images:
            return ""
        if isinstance(images, str):
            return self.format_image(images)
        elif isinstance(images, list):
            return '<div class="image-gallery">' + ''.join(self.format_image(img) for img in images) + '</div>'
        return ""
    
    def generate_list_html(self, items):
        rows = []
        for item in items:
            item_id = str(item.get('ID', item.get('_id', 'N/A')))
            title = str(item.get('Tiêu đề', 'N/A'))
            stt = str(item.get('stt', 'N/A'))
            hien_thi = self.format_boolean(item.get('Hiển thị', False))
            noi_bat = self.format_boolean(item.get('Nổi bật', False))
            ban_chay = self.format_boolean(item.get('Bán chạy', False))
            noi_bat_danh_muc = self.format_boolean(item.get('Nổi bật danh mục', False))
            
            rows.append(f"""
                <tr>
                    <td>{item_id}</td>
                    <td>{stt}</td>
                    <td><a href="/detail?id={item_id}" style="color: #2196F3; text-decoration: none;">{title}</a></td>
                    <td style="text-align: center;">{hien_thi}</td>
                    <td style="text-align: center;">{noi_bat}</td>
                    <td style="text-align: center;">{ban_chay}</td>
                    <td style="text-align: center;">{noi_bat_danh_muc}</td>
                </tr>
            """)
        
        return f"""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>Product List - Gạch Men Giá Tốt</title>
            <style>
                body {{ font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }}
                .container {{ max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }}
                h1 {{ color: #2196F3; text-align: center; margin-bottom: 30px; }}
                table {{ width: 100%; border-collapse: collapse; margin-top: 20px; }}
                th, td {{ padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }}
                th {{ background-color: #2196F3; color: white; font-weight: bold; }}
                tr:hover {{ background-color: #f5f5f5; }}
                a {{ color: #2196F3; text-decoration: none; }}
                a:hover {{ text-decoration: underline; }}
            </style>
        </head>
        <body>
            <div class="container">
                <h1>🏠 Danh Sách Sản Phẩm Gạch</h1>
                <p style="text-align: center; color: #666;">Tổng cộng: <strong>{len(items)}</strong> sản phẩm</p>
                <table>
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>STT</th>
                            <th>Tiêu đề</th>
                            <th>Hiển thị</th>
                            <th>Nổi bật</th>
                            <th>Bán chạy</th>
                            <th>Nổi bật danh mục</th>
                        </tr>
                    </thead>
                    <tbody>
                        {''.join(rows)}
                    </tbody>
                </table>
            </div>
        </body>
        </html>
        """
    
    def generate_detail_html(self, list_item, detail_item, item_id):
        # Combine data from both sources
        data = {}
        if list_item:
            data.update(list_item)
        if detail_item:
            data.update(detail_item)
        
        title = str(data.get('Tiêu đề', data.get('Tên', f'Product {item_id}')))
        
        # Generate field rows with proper ordering
        field_rows = []
        skip_fields = {'_id', '_itemIndex', '_parentId', 'url'}
        
        # Define field order for better display
        important_fields = ['Tiêu đề', 'Tên', 'Giá bán', 'Giá mới', 'Danh mục cấp 1', 'Danh mục cấp 2', 'Danh mục cấp 3']
        image_fields = ['Hình đại diện', 'Hình hiện tại', 'Hình hover hiện tại', 'Album hiện tại']
        content_fields = ['Mô tả', 'Nội dung']
        seo_fields = ['SEO Title', 'SEO Description', 'SEO Keywords', 'SEO H1', 'SEO H2', 'SEO H3']
        boolean_fields = ['Hiển thị', 'Nổi bật', 'Bán chạy', 'Nổi bật danh mục']
        
        # Process fields in order
        all_ordered_fields = important_fields + image_fields + content_fields + boolean_fields + seo_fields
        processed_fields = set()
        
        # First, process ordered fields
        for field_name in all_ordered_fields:
            if field_name in data and field_name not in skip_fields:
                self.add_field_row(field_rows, field_name, data[field_name])
                processed_fields.add(field_name)
        
        # Then process remaining fields
        for key, value in data.items():
            if key not in processed_fields and not key.startswith('_') and key not in skip_fields:
                self.add_field_row(field_rows, key, value)
        
        return f"""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>{title} - Chi Tiết Sản Phẩm</title>
            <style>
                body {{ font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; line-height: 1.6; }}
                .container {{ max-width: 1000px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }}
                .header {{ text-align: center; margin-bottom: 30px; padding-bottom: 20px; border-bottom: 2px solid #2196F3; }}
                h1 {{ color: #2196F3; margin-bottom: 10px; }}
                .back-link {{ display: inline-block; margin-bottom: 20px; padding: 10px 20px; background: #2196F3; color: white; text-decoration: none; border-radius: 5px; }}
                .back-link:hover {{ background: #1976D2; }}
                table {{ width: 100%; border-collapse: collapse; margin-top: 20px; }}
                th, td {{ padding: 15px; border: 1px solid #ddd; }}
                .image-gallery {{ display: flex; flex-wrap: wrap; gap: 10px; }}
                .image-gallery img {{ max-width: 150px; height: auto; border: 1px solid #ddd; border-radius: 4px; }}
                .html-content {{ line-height: 1.8; }}
                .html-content p {{ margin-bottom: 10px; }}
                .html-content h1, .html-content h2, .html-content h3 {{ color: #2196F3; margin-top: 20px; margin-bottom: 10px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <a href="/" class="back-link">← Quay lại danh sách</a>
                
                <div class="header">
                    <h1>{title}</h1>
                    <p style="color: #666;">ID: {item_id}</p>
                </div>
                
                <table>
                    {''.join(field_rows)}
                </table>
            </div>
        </body>
        </html>
        """
    
    def add_field_row(self, field_rows, key, value):
        formatted_value = ""
        if value is None or value == "":
            formatted_value = '<em style="color: #999;">Không có dữ liệu</em>'
        elif isinstance(value, bool):
            formatted_value = self.format_boolean(value)
        elif 'giá' in key.lower() or 'price' in key.lower():
            formatted_value = self.format_price(value)
        elif any(img_key in key.lower() for img_key in ['hình', 'image', 'album']):
            formatted_value = self.format_images(value)
        elif key in ['Mô tả', 'Nội dung', 'SEO Description', 'SEO H1', 'SEO H2', 'SEO H3'] and value:
            formatted_value = f'<div class="html-content">{self.format_html_content(value)}</div>'
        else:
            formatted_value = str(value)
        
        field_rows.append(f"""
            <tr>
                <td style="font-weight: bold; background-color: #f8f9fa; width: 200px; vertical-align: top;">{key}</td>
                <td style="vertical-align: top;">{formatted_value}</td>
            </tr>
        """)

def find_data_directory():
    """Find the data directory, checking multiple possible locations"""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    current_dir = os.getcwd()
    
    # Check environment variable first
    env_data_dir = os.environ.get('CRAWLER_DATA_DIR')
    if env_data_dir:
        if os.path.exists(env_data_dir) and os.path.isdir(env_data_dir):
            print(f"✅ Found data directory from environment variable: {env_data_dir}")
            return os.path.abspath(env_data_dir)
        else:
            print(f"⚠️ Environment variable CRAWLER_DATA_DIR points to non-existent directory: {env_data_dir}")
    
    # Possible locations for data directory
    possible_paths = [
        os.path.join(current_dir, 'data'),           # Current working directory
        os.path.join(script_dir, 'data'),            # Same directory as script
        os.path.join(os.path.dirname(script_dir), 'data'),  # Parent directory
        'data',                                       # Relative to current dir
        '/root/data',                                # Absolute path on server
    ]
    
    for path in possible_paths:
        if os.path.exists(path) and os.path.isdir(path):
            print(f"✅ Found data directory at: {path}")
            return os.path.abspath(path)
    
    return None

def run_server(port=80):
    
    # Find the data directory
    data_dir = find_data_directory()
    if not data_dir:
        print("❌ Could not find data directory in any of these locations:")
        print("   • Environment variable: CRAWLER_DATA_DIR")
        print("   • ./data")
        print("   • <script_dir>/data") 
        print("   • <script_parent>/data")
        print("   • /root/data")
        print("\nPlease either:")
        print("   1. Set environment variable: export CRAWLER_DATA_DIR=/path/to/data")
        print("   2. Ensure the data folder exists in one of the above locations")
        sys.exit(1)
    
    # Create handler class with data directory
    def handler_factory(*args, **kwargs):
        return CrawlerDataHandler(*args, data_dir=data_dir, **kwargs)
    
    # Bind to all interfaces (0.0.0.0) instead of localhost only
    server_address = ('0.0.0.0', port)
    httpd = HTTPServer(server_address, handler_factory)
    
    print(f"🚀 Server running at http://0.0.0.0:{port}")
    print(f"🌐 Access externally at http://127.0.0.1:{port}")
    print(f"📁 Using data directory: {data_dir}")
    print("📋 Available endpoints:")
    print(f"   • List view: http://127.0.0.1:{port}/")
    print(f"   • Detail view: http://127.0.0.1:{port}/detail?id=<item_id>")
    print("Press Ctrl+C to stop the server")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n⛔ Server stopped")
        httpd.server_close()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Run crawler data web server')
    parser.add_argument('--port', '-p', type=int, default=80, 
                        help='Port to run the server on (default: 80)')
    
    args = parser.parse_args()
    run_server(args.port)
