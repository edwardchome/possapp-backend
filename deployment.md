Below is a step-by-step, copy/paste deployment guide for a Spring Boot JAR to a Linux VPS using systemd + Nginx (HTTPS) + logs. This matches your setup style (Next.js already on nginx, API on localhost:8080).

⸻

0) What you’ll end up with
	•	Spring Boot runs as a service: possapp-service
	•	App listens on: 127.0.0.1:8080
	•	Public URL: https://inventory-app.net.tr/api/... → proxied to http://127.0.0.1:8080/api/...
	•	Logs stored in: /opt/poss-app/logs/

⸻

1) Build the JAR (on your Mac or on the server)

From your project folder:
```
mvn -U clean package -DskipTests
ls -lah target/*.jar
```
Assume jar path is: target/inventory-app-1.0.0.jar

⸻

2) Copy the JAR to the Linux server

From your Mac:
```
scp  target/*.jar myuser@inventory-app.net.tr:/tmp/app.jar
```
(Use your real SSH port; if it’s 22 remove -P 2222.)

⸻

3) Install Java 17 on the server

SSH into the server:
```
ssh myuser@inventory-app.net.tr
```

Install Java:
```
sudo apt update
sudo apt install -y openjdk-17-jre
/usr/bin/java -version
```

⸻

4) Create app folder + move jar + permissions
```
sudo mkdir -p /opt/inventory-app/logs
sudo mv /tmp/app.jar /opt/inventory-app/app.jar
sudo chown -R myuser:myuser /opt/inventory-app
sudo chmod 755 /opt/inventory-app
sudo chmod 644 /opt/inventory-app/app.jar
```

⸻

5) (Recommended) Create an environment file for secrets

Create:
```
sudo nano /etc/inventory-app.env
```
Example (edit values):
```
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USER=your@email.com
MAIL_PASS=your_app_password
```
Secure it:
```
sudo chown root:root /etc/inventory-app.env
sudo chmod 600 /etc/inventory-app.env
```

⸻

6) Create a systemd service

Create:
```
sudo nano /etc/systemd/system/inventory.service
```
Paste:
```
[Unit]
Description=Inventory App (Spring Boot)
After=network.target

[Service]
Type=simple
User=sisuser
Group=sisuser
WorkingDirectory=/opt/inventory-app

EnvironmentFile=/etc/inventory-app.env

ExecStart=/usr/bin/java -jar /opt/inventory-app/app.jar

Restart=on-failure
RestartSec=3

StandardOutput=append:/opt/inventory-app/logs/app.log
StandardError=append:/opt/inventory-app/logs/error.log

[Install]
WantedBy=multi-user.target
```
Enable + start:

```
sudo systemctl daemon-reload
sudo systemctl enable inventory
sudo systemctl restart inventory
sudo systemctl status inventory --no-pager
```
View logs:
```
sudo journalctl -u inventoryservice -n 200 --no-pager
tail -n 200 /opt/inventory-app/logs/app.log
tail -n 200 /opt/inventory-app/logs/error.log
```
Test locally:
```
curl -i http://127.0.0.1:8080/
```

⸻

7) Configure Nginx to route /api to Spring Boot /api

Edit your existing site:

sudo nano /etc/nginx/sites-available/inventory.com

Inside the 443 SSL server block, add this before location / { ... }:
```
location /api/ {
    proxy_pass http://127.0.0.1:8080;   # keep /api on both sides (no trailing slash)
    proxy_http_version 1.1;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    proxy_connect_timeout 60s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
}

```
Reload nginx:
```
sudo nginx -t
sudo systemctl reload nginx
```
Test public endpoint:
```
curl -i https://inventory.com/api/
```

⸻

8) Deploy updates (new jar) safely

Build the JAR (on your Mac or on the server)

From your project folder:
```
mvn -U clean package -DskipTests
ls -lah target/*.jar
```

From your Mac: copy jar to your server
```
scp  target/*.jar myuser@inventory-app.net.tr:/tmp/app.jar
```

When you upload a new jar to /tmp/app.jar:

```
ssh myuser@inventory-app.net.tr
```
```
sudo systemctl stop inventory
sudo mv /opt/inventory-app/app.jar /opt/inventory-app/app.jar.bak.$(date +%F-%H%M)
sudo mv /tmp/app.jar /opt/inventory-app/app.jar
sudo chown myuser:myuser /opt/inventory-app/app.jar
sudo systemctl start inventory
sudo systemctl status inventory --no-pager
tail -n 100 /opt/inventory-app/logs/error.log
tail -n 100 /opt/inventory-app/logs/app.log
```

⸻

9) Quick troubleshooting commands

Service won’t start
```
sudo systemctl reset-failed sistemdil-emailservice
sudo journalctl -u inventory -n 200 --no-pager
```
Port conflict
```
sudo ss -tulpn | grep :8080
```
Nginx 502
```
sudo tail -n 200 /var/log/nginx/error.log
```

⸻

