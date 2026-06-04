# Cloud MySQL Setup

Use this when the backend should store contracts in the cloud MySQL instance instead of local MySQL.

## 1. Initialize Database

Open PowerShell in the project root:

```powershell
cd "C:\Users\amc\Documents\agent—Contract"
```

Set connection variables:

```powershell
$env:MYSQL_HOST="119.29.163.90"
$env:MYSQL_PORT="3306"
$env:MYSQL_DATABASE="contract_agent"
$env:MYSQL_USER="root"
$env:MYSQL_PWD="your_mysql_password"
```

Import schema:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\init-cloud-mysql.ps1
```

## 2. Run Backend With Cloud Profile

Use environment variables so credentials are not committed to source code:

```powershell
$env:SPRING_PROFILES_ACTIVE="cloud"
$env:MYSQL_HOST="119.29.163.90"
$env:MYSQL_PORT="3306"
$env:MYSQL_DATABASE="contract_agent"
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="your_mysql_password"
$env:KIMI_API_KEY="your_kimi_api_key"
$env:KIMI_MODEL="moonshot-v1-8k"

cd backend
mvn.cmd spring-boot:run
```

Or run the packaged jar:

```powershell
java -jar .\target\contract-agent-0.1.0.jar
```

## 3. Verify

Create a contract through the frontend or API, then check:

```powershell
mysql -h 119.29.163.90 -P 3306 -u root -p contract_agent -e "SELECT contract_no,title,status FROM contract ORDER BY id DESC LIMIT 5;"
```

## Recommendation

For production-like use, avoid remote `root`. Create a project user:

```sql
CREATE USER 'contract_user'@'%' IDENTIFIED BY 'strong_password';
GRANT ALL PRIVILEGES ON contract_agent.* TO 'contract_user'@'%';
FLUSH PRIVILEGES;
```
