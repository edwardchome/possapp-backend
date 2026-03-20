#!/bin/bash

# PossApp Backend Management Script (for systemd)
#
# Usage:
#   ./manage.sh start      - Start the service
#   ./manage.sh stop       - Stop the service
#   ./manage.sh restart    - Restart the service
#   ./manage.sh status     - Check service status
#   ./manage.sh logs       - View live logs
#   ./manage.sh update     - Update JAR file and restart

set -e

SERVICE_NAME="possapp"
JAR_FILE="target/possapp-backend-1.0.0.jar"
INSTALL_DIR="/opt/possapp"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

show_help() {
    echo "PossApp Backend Management"
    echo ""
    echo "Usage: ./manage.sh [command]"
    echo ""
    echo "Commands:"
    echo "  start     Start the service"
    echo "  stop      Stop the service"
    echo "  restart   Restart the service"
    echo "  status    Show service status"
    echo "  logs      View live logs (Ctrl+C to exit)"
    echo "  update    Build, copy JAR, and restart"
    echo "  install   Run systemd installation"
    echo ""
}

cmd_start() {
    echo -e "${BLUE}Starting $SERVICE_NAME...${NC}"
    sudo systemctl start $SERVICE_NAME
    sleep 2
    check_status
}

cmd_stop() {
    echo -e "${BLUE}Stopping $SERVICE_NAME...${NC}"
    sudo systemctl stop $SERVICE_NAME
    echo -e "${GREEN}✓ Stopped${NC}"
}

cmd_restart() {
    echo -e "${BLUE}Restarting $SERVICE_NAME...${NC}"
    sudo systemctl restart $SERVICE_NAME
    sleep 2
    check_status
}

cmd_status() {
    echo -e "${BLUE}Service Status:${NC}"
    sudo systemctl status $SERVICE_NAME --no-pager
    echo ""
    echo -e "${BLUE}Recent Logs:${NC}"
    sudo journalctl -u $SERVICE_NAME --no-pager -n 10
}

check_status() {
    if sudo systemctl is-active --quiet $SERVICE_NAME; then
        echo -e "${GREEN}✓ $SERVICE_NAME is running${NC}"
        echo ""
        echo "Health Check:"
        curl -s http://localhost:8080/actuator/health | grep -o '"status":"[^"]*"' || echo "Health endpoint not responding"
    else
        echo -e "${RED}✗ $SERVICE_NAME is not running${NC}"
    fi
}

cmd_logs() {
    echo -e "${BLUE}Showing logs (Ctrl+C to exit)...${NC}"
    sudo journalctl -u $SERVICE_NAME -f
}

cmd_update() {
    echo -e "${BLUE}Building JAR file...${NC}"
    mvn clean package -DskipTests -q
    
    if [ ! -f "$JAR_FILE" ]; then
        echo -e "${RED}✗ Build failed!${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Build successful${NC}"
    echo ""
    echo -e "${BLUE}Stopping service...${NC}"
    sudo systemctl stop $SERVICE_NAME
    
    echo -e "${BLUE}Copying JAR file...${NC}"
    sudo cp "$JAR_FILE" "$INSTALL_DIR/possapp-backend-1.0.0.jar"
    sudo chown possapp:possapp "$INSTALL_DIR/possapp-backend-1.0.0.jar"
    
    echo -e "${BLUE}Starting service...${NC}"
    sudo systemctl start $SERVICE_NAME
    sleep 3
    
    check_status
}

cmd_install() {
    echo -e "${BLUE}Running systemd installation...${NC}"
    sudo ./install-systemd.sh
}

# Main
case "${1:-help}" in
    start)
        cmd_start
        ;;
    stop)
        cmd_stop
        ;;
    restart)
        cmd_restart
        ;;
    status)
        cmd_status
        ;;
    logs)
        cmd_logs
        ;;
    update)
        cmd_update
        ;;
    install)
        cmd_install
        ;;
    help|--help|-h|*)
        show_help
        ;;
esac
