# Скрипт для запуска среды разработки проекта "Educational Institution"

# --- Конфигурация ---
$serverDir = ".\server" # Относительно местоположения скрипта
$clientDir = ".\client" # Относительно местоположения скрипта

# Имя JAR-файла сервера (обновите, если отличается)
$serverJarName = "rest-api-java-js-1.0-SNAPSHOT.jar"
# $serverJarPath будет построен относительно $PSScriptRoot позже
$serverJarRelativePath = Join-Path -Path $serverDir -ChildPath "target\$serverJarName"

$clientPort = 8001
$clientUrl = "http://localhost:$clientPort" # URL по умолчанию, если http-server запустится

$defaultBrowser = "chrome" # или "msedge", "firefox", или путь к .exe браузера

# Проверка наличия инструментов Node.js
$nodeExists = Get-Command node -ErrorAction SilentlyContinue
$npmExists = Get-Command npm -ErrorAction SilentlyContinue
$npxExists = Get-Command npx -ErrorAction SilentlyContinue

# --- Функции ---
function Start-JavaServer {
    param(
        [string]$jarPath,        # Ожидается АБСОЛЮТНЫЙ путь к JAR-файлу
        [string]$workDir         # Ожидается АБСОЛЮТНЫЙ путь к рабочей директории
    )
    Write-Host "Попытка запуска Java-сервера из JAR: '$jarPath'"
    Write-Host "Рабочая директория Java-сервера будет: '$workDir'"

    if (-not (Test-Path $jarPath)) {
        Write-Error "JAR-файл сервера не найден: $jarPath. Пожалуйста, соберите сервер (например, mvn clean package)."
        # Рассмотрите возможность не выходить здесь, а позволить основному скрипту обработать это, или перебросить исключение
        throw "JAR-файл сервера не найден: $jarPath"
    }

    # Команда для выполнения в новом окне PowerShell
    # $jarPath теперь абсолютный, поэтому 'cd' не нарушает его разрешение
    # 'cd $workDir' устанавливает рабочую директорию для Java-процесса
    $command = "cd '$workDir'; Write-Host 'Текущая директория для Java-сервера: $(Get-Location)'; Write-Host 'Запуск Java-сервера командой: java -jar `"$jarPath`"...'; java -jar '$jarPath'"

    Write-Host "Запуск нового окна PowerShell с командой: $command"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $command -WindowStyle Normal
    Write-Host "Процесс запуска Java-сервера инициирован в отдельном окне. Держите его открытым."
} # Конец функции Start-JavaServer

function Start-ClientServer {
    param(
        [string]$absoluteClientPath, # Ожидается АБСОЛЮТНЫЙ путь
        [int]$port
    )
    Write-Host "Запуск клиентского http-сервера в '$absoluteClientPath' на порту $port..."
    if (-not $npxExists) {
        Write-Warning "npx не найден. Попытка использовать http-server напрямую (если установлен глобально)."
        Write-Warning "Если http-server не установлен глобально, этот шаг может завершиться ошибкой."
        Write-Warning "Рекомендуется установить Node.js и npm/npx."
    }

    $command = "cd '$absoluteClientPath'; Write-Host 'Запуск http-сервера...'; npx http-server -p $port -c-1 --cors"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $command -WindowStyle Normal
    Write-Host "Клиентский http-сервер запущен в отдельном окне на порту $port. Держите его открытым."
} # Конец функции Start-ClientServer

function Open-Browser {
    param(
        [string]$url,
        [string]$browser
    )
    Write-Host "Открытие '$url' в браузере '$browser'..."
    try {
        Start-Process $browser $url
    } catch {
        Write-Warning "Не удалось открыть URL с помощью '$browser'. Попытка использовать браузер по умолчанию."
        try {
            Start-Process $url # Попытка открыть с помощью браузера по умолчанию
        } catch {
            Write-Error "Не удалось открыть URL и с помощью браузера по умолчанию. Ошибка: $($_.Exception.Message)"
        }
    }
} # Конец функции Open-Browser


# --- Основная логика скрипта ---
Write-Host "--- Начало настройки среды разработки ---"

# 1. Убедимся, что мы находимся в директории скрипта, чтобы относительные пути работали корректно изначально
Push-Location $PSScriptRoot
try {
    # Преобразуем относительные пути из конфигурации в абсолютные пути на основе местоположения скрипта
    $absoluteServerDir = (Resolve-Path $serverDir).Path
    $absoluteServerJarPath = (Resolve-Path $serverJarRelativePath).Path
    $absoluteClientDir = (Resolve-Path $clientDir).Path
    $absoluteClientIndexHtmlPath = (Resolve-Path (Join-Path $clientDir 'index.html')).Path


    # 2. Сборка Java-сервера, если отсутствует папка target или JAR-файл
    # Test-Path использует абсолютный путь к JAR для ясности
    if (-not (Test-Path $absoluteServerJarPath)) {
        Write-Warning "JAR-файл сервера '$absoluteServerJarPath' не найден."
        if (-not (Test-Path (Join-Path -Path $absoluteServerDir -ChildPath "target"))) {
            Write-Warning "Папка 'target' сервера не найдена в '$absoluteServerDir'."
        }
        Write-Warning "Попытка собрать сервер..."
        try {
            $pomPath = Join-Path -Path $absoluteServerDir -ChildPath "pom.xml"
            if (-not (Test-Path $pomPath)) {
                Write-Error "pom.xml не найден по пути '$pomPath'. Сборка невозможна."
                exit 1 # Выход из скрипта, если pom.xml не найден
            }
            Write-Host "Выполнение 'mvn clean package' в '$absoluteServerDir' с использованием POM: '$pomPath'..."
            # Выполняем mvn в его директории
            Push-Location $absoluteServerDir
            try {
                & mvn -f "pom.xml" clean package # mvn должен найти pom.xml в текущей рабочей директории
                if ($LASTEXITCODE -ne 0) {
                    Write-Error "Сборка Maven не удалась. Пожалуйста, проверьте вывод. Код выхода: $LASTEXITCODE"
                    exit 1 # Выход из скрипта при ошибке сборки
                }
                Write-Host "Сборка сервера успешно завершена."
            } finally {
                Pop-Location # Возврат из $absoluteServerDir в $PSScriptRoot
            }

            # Проверка существования JAR после сборки
            if (-not (Test-Path $absoluteServerJarPath)) {
                 Write-Error "JAR-файл сервера '$absoluteServerJarPath' все еще не найден после сборки. Пожалуйста, проверьте конфигурацию Maven и вывод."
                 exit 1
            }

        } catch {
            Write-Error "Ошибка при попытке сборки Maven: $($_.Exception.Message)"
            exit 1 # Выход из скрипта при исключении во время сборки
        }
    }

    # Запуск Java-сервера
    Start-JavaServer -jarPath $absoluteServerJarPath -workDir $absoluteServerDir
    Write-Host "Ожидание запуска Java-сервера (приблизительно 15 секунд)..."
    Start-Sleep -Seconds 15

    # 3. Запуск клиентского http-сервера (если доступен Node.js/npx)
    $currentClientUrl = $clientUrl # По умолчанию из конфигурации
    if ($npxExists) {
        Start-ClientServer -absoluteClientPath $absoluteClientDir -port $clientPort
        Write-Host "Ожидание запуска клиентского сервера (приблизительно 5 секунд)..."
        Start-Sleep -Seconds 5
    } else {
        Write-Warning "npx (часть Node.js) не найден. Клиентский http-сервер не будет запущен автоматически."
        Write-Warning "Вы можете попробовать открыть '$($clientDir -replace '\\','/')/index.html' непосредственно в браузере,"
        Write-Warning "или запустить статический файловый сервер (например, 'python -m http.server' или 'http-server') в '$absoluteClientDir' вручную."

        # Обновляем $currentClientUrl, чтобы он указывал на локальный файл
        $currentClientUrl = "file:///$($absoluteClientIndexHtmlPath.Replace('\', '/'))"
        Write-Host "Для просмотра клиента попробуйте открыть этот путь к локальному файлу в браузере: $currentClientUrl"
    }

    # 4. Открытие клиентского приложения в браузере
    Open-Browser -url $currentClientUrl -browser $defaultBrowser

    Write-Host ""
    Write-Host "--- Скрипт настройки среды разработки завершен. ---"
    Write-Host "Java-сервер и клиентский сервер (если запущен) работают в отдельных окнах."
    Write-Host "Не забудьте остановить серверы (Ctrl+C в их соответствующих окнах), когда закончите."

} catch {
    Write-Error "Произошла ошибка в основном скрипте: $($_.Exception.Message)"
    # $_ может предоставить больше деталей об ошибке
    Write-Error "Выполнение скрипта не удалось."
} finally {
    Pop-Location # Возврат в исходную директорию, откуда был запущен скрипт
    Write-Host "Выполнение скрипта завершено. Восстановлено исходное местоположение: $(Get-Location)"
}