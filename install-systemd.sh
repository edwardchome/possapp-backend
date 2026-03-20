#!/bin/bash

# PossApp Systemd Service Installation Script
#
# This script installs and configures the PossApp backend as a systemd service
#
# Usage:
#   sudo ./install-systemd.sh

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}PossApp Systemd Service Installer${NC}"
echo "===================================="
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}Error: Please run as root (use sudo)${NC}"
    exit 1
fi

# Configuration
APP_USER="possapp"
APP_GROUP="possapp"
APP_DIR="/opt/possapp"
LOG_DIR="/var/log/possapp"
SERVICE_FILE="possapp.service"
SYSTEMD_DIR="/etc/systemd/system"
JAR_SOURCE="target/possapp-backend-1.0.0.jar"

# Check if JAR file exists
if [ ! -f "$JAR_SOURCE" ]; then
    echo -e "${RED}Error: $JAR_SOURCE not found!${NC}"
    echo "Make sure to build the project first:"
    echo "  cd ~/projects/mobile/poss_mobile_and_backend/backend_api"
    echo "  mvn clean package -DskipTests"
    exit 1
fi

echo "Step 1: Creating user and group..."
if ! id "$APP_USER" &>/dev/null; then
    useradd -r -s /bin/false -d "$APP_DIR" -m "$APP_USER"
    echo -e "${GREEN}✓ Created user: $APP_USER${NC}"
else
    echo -e "${YELLOW}⚠ User $APP_USER already exists${NC}"
fi

echo ""
echo "Step 2: Creating directories..."
mkdir -p "$APP_DIR"
mkdir -p "$LOG_DIR"
chown -R "$APP_USER:$APP_GROUP" "$APP_DIR"
chown -R "$APP_USER:$APP_GROUP" "$LOG_DIR"
echo -e "${GREEN}✓ Created $APP_DIR${NC}"
echo -e "${GREEN}✓ Created $LOG_DIR${NC}"

echo ""
echo "Step 3: Copying JAR file..."
cp "$JAR_SOURCE" "$APP_DIR/possapp-backend-1.0.0.jar"
chown "$APP_USER:$APP_GROUP" "$APP_DIR/possapp-backend-1.0.0.jar"
echo -e "${GREEN}✓ JAR file installed${NC}"

echo ""
echo "Step 4: Installing systemd service..."
cp "$SERVICE_FILE" "$SYSTEMD_DIR/"
chmod 644 "$SYSTEMD_DIR/$SERVICE_FILE"
echo -e "${GREEN}✓ Service file installed${NC}"

echo ""
echo "Step 5: Reloading systemd..."
systemctl daemon-reload
echo -e "${GREEN}✓ Systemd reloaded${NC}"

echo ""
echo "Step 6: Enabling service..."
systemctl enable possapp.service
echo -e "${GREEN}✓ Service enabled${NC}"

echo ""
echo -e "${GREEN}====================================${NC}"
echo "Installation complete!"
echo ""
echo "To start the service:"
echo "  sudo systemctl start possapp"
echo ""
echo "To check status:"
echo "  sudo systemctl status possapp"
echo ""
echo "To view logs:"
echo "  sudo journalctl -u possapp -f"
echo ""
echo "Configuration file:"
echo "  $SYSTEMD_DIR/$SERVICE_FILE"
echo ""
echo -e "${YELLOW}Note: Edit the service file to set your database password and JWT secret${NC}"
