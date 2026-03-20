#!/bin/bash

# PossApp Backend Production Startup Script
# 
# Usage:
#   ./start.sh              - Start in production mode
#   ./start.sh dev          - Start in development mode
#   ./start.sh stop         - Stop the running application
#   ./start.sh status       - Check if application is running
#   ./start.sh logs         - View application logs

# Configuration
JAR_FILE="possapp-backend-1.0.0.jar"
LOG_FILE="app.log"
PID_FILE="app.pid"

# Environment Configuration
export APP_BASE_URL=https://inventory-app.net.tr
export SPRING_PROFILES_ACTIVE=prod

# Optional: Set other production environment variables
# Uncomment and set these as needed:
# export JWT_SECRET=your-secure-jwt-secret-here
# export DB_PASSWORD=your-database-password
# export SMTP_PASSWORD=your-smtp-password

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if app is running
is_running() {
    if [ -f "$PID_FILE" ]; then
        pid=$(cat "$PID_FILE")
        if ps -p "$pid" > /dev/null 2>&1; then
            return 0
        fi
    fi
    return 1
}

# Function to start the application
start_app() {
    if is_running; then
        echo -e "${YELLOW}Application is already running (PID: $(cat $PID_FILE))${NC}"
        exit 1
    fi

    echo -e "${GREEN}Starting PossApp Backend...${NC}"
    echo "Environment: Production"
    echo "Base URL: $APP_BASE_URL"
    echo "Timestamp: $(date)"
    echo "----------------------------------------"

    # Check if JAR file exists
    if [ ! -f "$JAR_FILE" ]; then
        echo -e "${RED}Error: $JAR_FILE not found!${NC}"
        echo "Make sure to build the project first: mvn clean package"
        exit 1
    fi

    # Start the application
    nohup java -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"

    sleep 3

    if is_running; then
        echo -e "${GREEN}✓ Application started successfully!${NC}"
        echo "PID: $(cat $PID_FILE)"
        echo "Logs: tail -f $LOG_FILE"
        echo ""
        echo "API Endpoint: $APP_BASE_URL/api/v1"
        echo "Health Check: $APP_BASE_URL/actuator/health"
    else
        echo -e "${RED}✗ Failed to start application${NC}"
        echo "Check logs: cat $LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi
}

# Function to stop the application
stop_app() {
    if ! is_running; then
        echo -e "${YELLOW}Application is not running${NC}"
        rm -f "$PID_FILE"
        return
    fi

    pid=$(cat "$PID_FILE")
    echo "Stopping application (PID: $pid)..."
    
    kill "$pid" 2>/dev/null
    
    # Wait for graceful shutdown
    for i in {1..10}; do
        if ! ps -p "$pid" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Application stopped${NC}"
            rm -f "$PID_FILE"
            return
        fi
        sleep 1
    done
    
    # Force kill if still running
    echo "Force stopping..."
    kill -9 "$pid" 2>/dev/null
    rm -f "$PID_FILE"
    echo -e "${GREEN}✓ Application stopped${NC}"
}

# Function to show status
show_status() {
    if is_running; then
        pid=$(cat "$PID_FILE")
        echo -e "${GREEN}✓ Application is running${NC}"
        echo "PID: $pid"
        echo "Uptime: $(ps -o etime= -p "$pid" 2>/dev/null || echo "unknown")"
        echo "Log file: $LOG_FILE"
        echo ""
        echo "Recent logs:"
        tail -n 5 "$LOG_FILE" 2>/dev/null || echo "No logs yet"
    else
        echo -e "${RED}✗ Application is not running${NC}"
        rm -f "$PID_FILE"
    fi
}

# Function to show logs
show_logs() {
    if [ ! -f "$LOG_FILE" ]; then
        echo "No log file found"
        return
    fi
    
    echo "Showing last 50 lines of logs (Ctrl+C to exit)..."
    tail -n 50 -f "$LOG_FILE"
}

# Function to start in development mode
start_dev() {
    export APP_BASE_URL=http://localhost:8080
    export SPRING_PROFILES_ACTIVE=dev
    
    echo -e "${YELLOW}Starting in DEVELOPMENT mode...${NC}"
    echo "Base URL: $APP_BASE_URL"
    
    java -jar "$JAR_FILE"
}

# Main script logic
case "${1:-start}" in
    start)
        start_app
        ;;
    dev|development)
        start_dev
        ;;
    stop)
        stop_app
        ;;
    restart)
        stop_app
        sleep 2
        start_app
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs
        ;;
    help|--help|-h)
        echo "PossApp Backend Startup Script"
        echo ""
        echo "Usage: ./start.sh [command]"
        echo ""
        echo "Commands:"
        echo "  start       Start in production mode (default)"
        echo "  dev         Start in development mode (foreground)"
        echo "  stop        Stop the running application"
        echo "  restart     Restart the application"
        echo "  status      Check application status"
        echo "  logs        View application logs"
        echo "  help        Show this help message"
        echo ""
        echo "Examples:"
        echo "  ./start.sh              # Start production server"
        echo "  ./start.sh dev          # Start development server"
        echo "  ./start.sh status       # Check if running"
        echo "  ./start.sh logs         # View logs"
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        echo "Use './start.sh help' for usage information"
        exit 1
        ;;
esac
