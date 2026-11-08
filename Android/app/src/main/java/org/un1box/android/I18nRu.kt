package org.un1box.android

/**
 * The Russian catalogue: what the app says, in Russian.
 *
 * [exact] holds whole labels and sentences, keyed by the English the composables pass to
 * [Text]. [rules] covers the ones with a changing part (a device name, a version, a
 * count); they are tried in order, so a specific sentence comes before a general one, and
 * a captured part is translated again when it is itself a known string ("Tested: ..." gets
 * the sentence after the colon too).
 *
 * Anything not listed falls through to English: a half-finished translation should read as
 * English, never as a blank or a mangled key.
 *
 * The English keys must stay byte for byte as the sources write them, or an entry quietly
 * stops matching. tests/i18n/run.py checks that.
 */
internal object I18nRu {
    private val exact: Map<String, String> = HashMap<String, String>(1414).apply {
        put("No", "Нет")
        put("OK", "ОК")
        put(" ms", " мс")
        put("How", "Способ")
        put("May", "Май")
        put("Off", "Выкл.")
        put("Yes", "Да")
        put(" FPS", " FPS")
        put(" or ", " или ")
        put("Back", "Назад")
        put("Chip", "Чип")
        put("Copy", "Копировать")
        put("Dark", "Тёмная")
        put("Full", "Полный")
        put("Home", "Домой")
        put("Info", "Информация")
        put("July", "Июль")
        put("June", "Июнь")
        put("More", "Ещё")
        put("Name", "Имя")
        put("None", "Нет")
        put("Save", "Сохранить")
        put("Stop", "Остановить")
        put(" and ", " и ")
        put("About", "О приложении")
        put("April", "Апрель")
        put("Audio", "Аудио")
        put("Basic", "Базовый")
        put("Board", "Плата")
        put("Build", "Сборка")
        put("Clear", "Очистить")
        put("Close", "Закрыть")
        put("Graph", "График")
        put("Known", "Известно")
        put("Light", "Светлая")
        put("March", "Март")
        put("Model", "Модель")
        put("Other", "Другое")
        put("Pause", "Пауза")
        put("Range", "Диапазон")
        put("Ready", "Готово")
        put("Retry", "Повторить")
        put("Saved", "Сохранено")
        put("Share", "Поделиться")
        put("Sharp", "Резкий")
        put("Start", "Запустить")
        put("State", "Состояние")
        put("Theme", "Тема")
        put("Total", "Всего")
        put("Worst", "Худший")
        put("1% low", "1% low")
        put("A to Z", "От A до Z")
        put("Accept", "Принять")
        put("August", "Август")
        put("Cancel", "Отмена")
        put("Charts", "Чарты")
        put("Chosen", "Выбрано")
        put("Delete", "Удалить")
        put("Detail", "Детализация")
        put("Device", "Устройство")
        put("Found ", "Найдено ")
        put("Jetsam", "Выгрузка памяти")
        put("Memory", "Память")
        put("Newest", "Сначала новые")
        put("Oldest", "Сначала старые")
        put("Paused", "Приостановлено")
        put("Record", "Запись")
        put("Rename", "Переименовать")
        put("Report", "Отчёт")
        put("Resume", "Продолжить")
        put("Screen", "Экран")
        put("Search", "Поиск")
        put("Smooth", "Сглаженный")
        put("System", "Системная")
        put("Tested", "Проверено")
        put("Thread", "Поток")
        put("Avg FPS", "Средний FPS")
        put("Copying", "Копирование")
        put("Details", "Подробности")
        put("Devices", "Устройства")
        put("Dismiss", "Закрыть")
        put("Display", "Дисплей")
        put("GL call", "Вызов GL")
        put("Hitches", "Рывки")
        put("Inspect", "Инспекция")
        put("Install", "Установить")
        put("January", "Январь")
        put("Not now", "Не сейчас")
        put("October", "Октябрь")
        put("Payload", "Payload")
        put("Process", "Процесс")
        put("Recover", "Восстановить")
        put("Running", "Работает")
        put("Saving…", "Сохранение…")
        put("Storage", "Хранилище")
        put("Thread ", "Поток ")
        put("Unknown", "Неизвестно")
        put("Waiting", "Ожидание")
        put("detail ", "детали ")
        put("All apps", "Все приложения")
        put("Camera: ", "Камера: ")
        put("Controls", "Управление")
        put("Crashed:", "Сбой:")
        put("December", "Декабрь")
        put("Download", "Скачать")
        put("Featured", "Рекомендуемые")
        put("February", "Февраль")
        put("Graphics", "Графика")
        put("Hardware", "Оборудование")
        put("Installs", "Установки")
        put("Language", "Язык")
        put("New Free", "Новые бесплатные")
        put("New Paid", "Новые платные")
        put("November", "Ноябрь")
        put("Position", "Положение")
        put("Prepared", "Подготовлено")
        put("Selected", "Выбрано")
        put("Settings", "Настройки")
        put("Starting", "Запускается")
        put("Tested: ", "Проверено: ")
        put("Top Free", "Топ бесплатных")
        put("Top Paid", "Топ платных")
        put("Top left", "Сверху слева")
        put("Untested", "Не проверено")
        put("Version ", "Версия ")
        put("app list", "список приложений")
        put("this iOS", "этой версии iOS")
        put("this one", "эта")
        put(" Crashed:", " Сбой:")
        put(" of flash", " флеш-памяти")
        put("1 version", "Версий: 1")
        put("Boot file", "Загрузочный файл")
        put("Cancelled", "Отменено")
        put("Disk size", "Размер диска")
        put("Downloads", "Загрузки")
        put("Guest CPU", "Гостевой CPU")
        put("Installed", "Установлено")
        put("Local IPA", "Локальный IPA")
        put("Lock hold", "Удержание блокировки")
        put("Lock wait", "Ожидание блокировки")
        put("Main loop", "Главный цикл")
        put("No device", "Нет устройства")
        put("Preparing", "Подготовка")
        put("Reinstall", "Переустановить")
        put("Released ", "Выпущено ")
        put("September", "Сентябрь")
        put("Shut down", "Выключить")
        put("Top right", "Сверху справа")
        put("Try again", "Попробовать снова")
        put("Unpacking", "Распаковка")
        put("Volume up", "Громкость +")
        put("boot file", "загрузочный файл")
        put("no report", "нет отчёта")
        put("not found", "не найдено")
        put("not ready", "не готово")
        put("unable to", "не удалось")
        put("Add device", "Добавить устройство")
        put("Background", "Фон")
        put("Camera off", "Камера выключена")
        put("Cancelling", "Отменяется")
        put("Categories", "Категории")
        put("Frame rate", "Частота кадров")
        put("Frame time", "Время кадра")
        put("GPU driver", "Драйвер GPU")
        put("Identifier", "Идентификатор")
        put("Installed ", "Установлено ")
        put("Installing", "Установка")
        put("Last crash", "Последний сбой")
        put("Measuring…", "Измерение…")
        put("Microphone", "Микрофон")
        put("OS Version", "Версия ОС")
        put("Preparing ", "Подготовка ")
        put("Recording…", "Запись…")
        put("Screenshot", "Снимок экрана")
        put("Show fewer", "Свернуть")
        put("iOS system", "Система iOS")
        put("warming up", "прогрев")
        put("Bottom left", "Снизу слева")
        put("Cancelling…", "Отменяется…")
        put("Collections", "Подборки")
        put("Diagnostics", "Диагностика")
        put("Downloading", "Загрузка")
        put("Engine logs", "Журналы движка")
        put("Full screen", "Во весь экран")
        put("Get started", "Начало работы")
        put("Guest tools", "Гостевые инструменты")
        put("None found.", "Ничего не найдено.")
        put("Performance", "Производительность")
        put("Save & Stop", "Сохранить и остановить")
        put("Save report", "Сохранить отчёт")
        put("Source code", "Исходный код")
        put("Termination", "Завершение")
        put("Volume down", "Громкость −")
        put("Why it quit", "Причина завершения")
        put("Your device", "Ваше устройство")
        put("back camera", "тыловая камера")
        put("Add a device", "Добавить устройство")
        put("All licenses", "Все лицензии")
        put("Bottom right", "Снизу справа")
        put("Cannot read ", "Не удалось прочитать ")
        put("Checking app", "Проверка приложения")
        put("Downloading ", "Загрузка ")
        put("Experimental", "Экспериментально")
        put("Frame times:", "Время кадров:")
        put("Guest paused", "Гостевая система приостановлена")
        put("Hide details", "Скрыть подробности")
        put("In the queue", "В очереди")
        put("Legacy Store", "Классический магазин")
        put("Start it now", "Запустить сейчас")
        put("Tested, slow", "Проверено, медленно")
        put("Top Grossing", "Топ по выручке")
        put("Un1box guest", "Гостевая система Un1box")
        put("Unpacking · ", "Распаковка · ")
        put("front camera", "фронтальная камера")
        put("is truncated", "усечён")
        put("no frame yet", "нет кадра")
        put("no such file", "нет такого файла")
        put("Called from: ", "Вызов из: ")
        put("Choose a file", "Выбрать файл")
        put("Close session", "Закрыть сеанс")
        put("Couldn't load", "Не удалось загрузить")
        put("Create device", "Создать устройство")
        put("Delete failed", "Не удалось удалить")
        put("Guest display", "Дисплей гостевой системы")
        put("Import failed", "Не удалось импортировать")
        put("Main loop CPU", "CPU главного цикла")
        put("Most archived", "Больше всего версий")
        put("No frames yet", "Кадров пока нет")
        put("On this phone", "Занято на телефоне")
        put("Rename device", "Переименовать устройство")
        put("Rename failed", "Не удалось переименовать")
        put("Requires iOS ", "Требуется iOS ")
        put("Running guest", "Гостевая система запущена")
        put("Search failed", "Не удалось выполнить поиск")
        put("Serial number", "Серийный номер")
        put("Show controls", "Показать элементы управления")
        put("Shutting down", "Завершение работы")
        put("Starting iOS…", "Запуск iOS…")
        put("Tilt controls", "Управление наклоном")
        put("Wi-Fi address", "Адрес Wi-Fi")
        put("Worker exited", "Рабочий процесс завершён")
        put("a few minutes", "несколько минут")
        put("Native GLES", "Нативный GLES")
        put("App in front: ", "Активное приложение: ")
        put("Binary Images:", "Бинарные образы:")
        put("Cannot create ", "Не удалось создать ")
        put("Clear finished", "Очистить завершённые")
        put("Copying files…", "Копирование файлов…")
        put("Crashed Thread", "Поток сбоя")
        put("Device deleted", "Устройство удалено")
        put("Engine (ARM64)", "Движок (ARM64)")
        put("Exception Type", "Тип исключения")
        put("Inspect button", "Кнопка «Инспекция»")
        put("Inspect frames", "Инспекция кадров")
        put("Install failed", "Не удалось установить")
        put("Later snapshot", "Более поздний снимок")
        put("Motion sensors", "Датчики движения")
        put("Needs recovery", "Требуется восстановление")
        put("No apps found.", "Приложения не найдены.")
        put("Prepare device", "Подготовить устройство")
        put("Presenter work", "Работа представления")
        put("Recommended · ", "Рекомендуется · ")
        put("Screen scaling", "Масштабирование экрана")
        put("Shutting down…", "Завершение работы…")
        put("about a minute", "около минуты")
        put("code signature", "подпись кода")
        put("And prepares it", "И подготовит его на этом устройстве")
        put("Cancel download", "Отменить скачивание")
        put("Choose a device", "Выберите устройство")
        put("Choose a folder", "Выберите папку")
        put("Detailed timing", "Подробный хронометраж")
        put("Download failed", "Скачивание не удалось")
        put("Engine (x86-64)", "Движок (x86-64)")
        put("Exception Codes", "Коды исключения")
        put("For this device", "Для этого устройства")
        put("Frame inspector", "Инспектор кадров")
        put("Haptic feedback", "Виброотклик")
        put("Name (optional)", "Имя (необязательно)")
        put("Prepared system", "Подготовленная система")
        put("Select a device", "Выберите устройство")
        put("Shown on screen", "Показано на экране")
        put("Slowest events:", "Самые медленные события:")
        put("vCPU accounting", "Учёт vCPU")
        put("Add device files", "Добавить файлы устройства")
        put("Cannot read file", "Не удалось прочитать файл")
        put("Close and resume", "Закрыть и продолжить")
        put("Could not save: ", "Не удалось сохранить: ")
        put("Earlier snapshot", "Более ранний снимок")
        put("Exit full screen", "Выйти из полноэкранного режима")
        put("Lock (BQL) waits", "Ожидания блокировки (BQL)")
        put("New & Noteworthy", "Новое и интересное")
        put("Record a profile", "Записать профиль")
        put("Android interface", "Интерфейс Android")
        put("Choose the folder", "Выберите папку")
        put("Copying to device", "Копирование на устройство")
        put("Doesn't start yet", "Пока не запускается")
        put("Download failed: ", "Скачивание не удалось: ")
        put("Exception Subtype", "Подтип исключения")
        put("Finished installs", "Завершённые установки")
        put("Installs finished", "Установки завершены")
        put("No apps added yet", "Приложения ещё не добавлены")
        put("None needed. The ", "Загрузочный файл не нужен. ")
        put("None over 0.5 ms.", "Нет событий дольше 0.5 мс.")
        put("Nothing found in ", "Ничего не найдено в ")
        put("Recover and start", "Восстановить и запустить")
        put("Screenshot failed", "Не удалось сделать снимок экрана")
        put("could not save it", "не удалось сохранить")
        put("no camera service", "нет службы камеры")
        put("640 × 960 (Retina)", "640 × 960 (Retina)")
        put("About this setting", "Об этой настройке")
        put("Changes since then", "Изменения с тех пор")
        put("Checking the file…", "Проверка файла…")
        put("Download cancelled", "Скачивание отменено")
        put("Hide beta versions", "Скрыть бета-версии")
        put("Invalid entry name", "Недопустимое имя записи")
        put("Missing entry name", "Отсутствует имя записи")
        put("No device selected", "Устройство не выбрано")
        put("Not in the archive", "Нет в архиве")
        put("Preparation failed", "Не удалось подготовить")
        put("Required · .itbase", "Требуется · .itbase")
        put("Share hitch report", "Поделиться отчётом о подтормаживаниях")
        put("Termination Reason", "Причина завершения")
        put("Un1box preparation", "Подготовка Un1box")
        put("Update guest tools", "Обновить гостевые инструменты")
        put("Waiting to install", "Ожидание установки")
        put("Worker unavailable", "Рабочий процесс недоступен")
        put("timing unavailable", "хронометраж недоступен")
        put("  of which GL calls", "  из них вызовы GL")
        put("Adding guest tools…", "Добавление гостевых инструментов…")
        put("Download cancelled.", "Скачивание отменено.")
        put("Dyld Error Message:", "Сообщение об ошибке Dyld:")
        put("Find apps and games", "Найти приложения и игры")
        put("Performance overlay", "Оверлей производительности")
        put("Present on a worker", "Представление в рабочем потоке")
        put("Show in device view", "Показать в представлении устройства")
        put("Try adding it again", "Попробуйте добавить ещё раз")
        put("Un1box downloads it", "Un1box скачает его")
        put("detailed timing off", "подробный хронометраж выключен")
        put("iOS firmware (IPSW)", "Прошивка iOS (IPSW)")
        put("vCPU on a host core", "vCPU на ядре хоста")
        put("waiting for frames…", "ожидание кадров…")
        put("(iOS logged nothing)", "(iOS ничего не записала)")
        put("Cannot read the IPSW", "Не удалось прочитать IPSW")
        put("Creating your device", "Создание устройства")
        put("Firmware preparation", "Подготовка прошивки")
        put("Guest app services: ", "Гостевые службы приложений: ")
        put("Install an IPA file…", "Установить файл IPA…")
        put("Installs in progress", "Идёт установка")
        put("Open source licenses", "Лицензии с открытым исходным кодом")
        put("Prepare from an IPSW", "Подготовить из IPSW")
        put("Stopped unexpectedly", "Неожиданно остановлено")
        put("Use a different file", "Использовать другой файл")
        put("——— Crash report ———", "——— Отчёт о сбое ———")
        put("——— Emulator log ———", "——— Журнал эмулятора ———")
        put("  of which GL present", "  из них представление GL")
        put("Camera frame failed: ", "Сбой кадра камеры: ")
        put("Checking the download", "Проверка скачивания")
        put("Copying the boot file", "Копирование загрузочного файла")
        put("Detailed frame timing", "Подробный хронометраж кадров")
        put("Headphones or speaker", "Наушники или динамик")
        put("Headphones plugged in", "Наушники подключены")
        put("Preparation cancelled", "Подготовка отменена")
        put("Recording the launch…", "Запись запуска…")
        put("Reusing the saved one", "Повторно используется сохранённый")
        put("Search apps and games", "Поиск приложений и игр")
        put("Select a device first", "Сначала выберите устройство")
        put("Shown on screen after", "Показано на экране после")
        put("Where do I get these?", "Где взять эти файлы?")
        put("device must be one of", "устройство должно быть одним из")
        put("iOS system, boot file", "Система iOS, загрузочный файл")
        put("Camera not started yet", "Камера ещё не запущена")
        put("Cannot read the folder", "Не удалось прочитать папку")
        put("Copying the iOS system", "Копирование системы iOS")
        put("Faithful scaler timing", "Точный хронометраж масштабирования")
        put("Firmware worker exited", "Рабочий процесс прошивки завершён")
        put("Paused · tap to resume", "Пауза · нажмите, чтобы продолжить")
        put("Prepared on a computer", "Подготовлено на компьютере")
        put("Profile capture failed", "Не удалось записать профиль")
        put("Reading crash reports…", "Чтение отчётов о сбоях…")
        put("Recover retained state", "Восстановить сохранённое состояние")
        put("Select a device first.", "Сначала выберите устройство.")
        put("Stop profile recording", "Остановить запись профиля")
        put("is not a guest package", "не является гостевым пакетом")
        put("the file did not match", "файл не совпал")
        put("vCPU halted or outside", "vCPU остановлен или вне диапазона")
        put("Cannot write screenshot", "Не удалось записать снимок экрана")
        put("Cannot write the report", "Не удалось записать отчёт")
        put("Classic iOS for Android", "Классическая iOS для Android")
        put("Copying the iOS system…", "Копирование системы iOS…")
        put("Could not save the IPSW", "Не удалось сохранить IPSW")
        put("Could not use that IPSW", "Не удалось использовать этот IPSW")
        put("Couldn't load the chart", "Не удалось загрузить чарт")
        put("Device selection failed", "Не удалось переключить устройство")
        put("Display input is paused", "Ввод для дисплея приостановлен")
        put("Guest tools not updated", "Гостевые инструменты не обновлены")
        put("Needs a newer processor", "Требуется более новый процессор")
        put("Unpause when you return", "Возобновить при возвращении")
        put("Applies to the whole app", "Действует для всего приложения")
        put("Cannot create screenshot", "Не удалось создать снимок экрана")
        put("Cannot start preparation", "Не удалось начать подготовку")
        put("Checking the iOS system…", "Проверка системы iOS…")
        put("Guest worker unavailable", "Гостевой рабочий процесс недоступен")
        put("No guest frame available", "Нет доступного гостевого кадра")
        put("Not made for this device", "Не предназначено для этого устройства")
        put("Not tested on Un1box yet", "Ещё не проверено на Un1box")
        put("Packing the iOS storage…", "Упаковка хранилища iOS…")
        put("Prepared output retained", "Результат подготовки сохранён")
        put("Screen off · tap to wake", "Экран выключен · нажмите, чтобы разбудить")
        put("iOS stopped unexpectedly", "iOS неожиданно остановилась")
        put("Cannot read the boot file", "Не удалось прочитать загрузочный файл")
        put("Device worker unavailable", "Рабочий процесс устройства недоступен")
        put("Display ownership retired", "Владение дисплеем аннулировано")
        put("Frame history unavailable", "История кадров недоступна")
        put("Graphics provider (ANGLE)", "Графический провайдер (ANGLE)")
        put("Guest code (vCPU running)", "Гостевой код (vCPU выполняется)")
        put("Guest tools not updated: ", "Гостевые инструменты не обновлены: ")
        put("Last Exception Backtrace:", "Стек последнего исключения:")
        put("Slow events in this frame", "Медленные события в этом кадре")
        put("Unpacking the iOS system…", "Распаковка системы iOS…")
        put("Device library unavailable", "Библиотека устройств недоступна")
        put("Invalid guest audio format", "Недопустимый формат гостевого аудио")
        put("Invalid guest iOS version.", "Недопустимая версия iOS у гостевой системы.")
        put("Loading the firmware list…", "Загрузка списка прошивок…")
        put("No prepared iOS system in ", "Нет подготовленной системы iOS в ")
        put("Setting up the iOS system…", "Настройка системы iOS…")
        put("Shutdown was not confirmed", "Завершение работы не подтверждено")
        put("Why did the last app quit?", "Почему завершилось последнее приложение?")
        put("the download stopped early", "скачивание прервалось досрочно")
        put("warming up (counter reset)", "прогрев (счётчик сброшен)")
        put("Could not create the device", "Не удалось создать устройство")
        put("Couldn't reach Legacy Store", "Не удалось подключиться к Legacy Store")
        put("Firmware preparation failed", "Не удалось подготовить прошивку")
        put("Firmware worker unavailable", "Рабочий процесс прошивки недоступен")
        put("Follow the phone's language", "Использовать язык телефона")
        put("Guest worker restart failed", "Не удалось перезапустить гостевой рабочий процесс")
        put("Include apps that won't run", "Включить приложения, которые не запустятся")
        put("License could not be read: ", "Не удалось прочитать лицензию: ")
        put("Not available in this build", "Недоступно в этой сборке")
        put("Slow engine events in range", "Медленные события движка в диапазоне")
        put("That file is not a boot ROM", "Этот файл не является загрузочным ROM")
        put("iPod touch (2nd generation)", "iPod touch (2-го поколения)")
        put("iPod touch (3rd generation)", "iPod touch (3-го поколения)")
        put("iPod touch (4th generation)", "iPod touch (4-го поколения)")
        put("the camera was disconnected", "камера была отключена")
        put("Could not use that boot file", "Не удалось использовать этот загрузочный файл")
        put("Couldn't load the categories", "Не удалось загрузить категории")
        put("Device library unavailable: ", "Библиотека устройств недоступна: ")
        put("Download no longer available", "Скачивание больше недоступно")
        put("Experimental cache selection", "Экспериментальный выбор кэша")
        put("Prepare from an IPSW instead", "Подготовить из IPSW вместо этого")
        put("Start the device to install.", "Запустите устройство, чтобы установить.")
        put("Un1box finds the files in it", "Un1box сам найдёт в ней файлы")
        put(", and the device's boot file.", ", а также загрузочный файл устройства.")
        put("Couldn't load the collections", "Не удалось загрузить подборки")
        put("Firmware worker unavailable: ", "Рабочий процесс прошивки недоступен: ")
        put("Invalid universal executable.", "Недопустимый универсальный исполняемый файл.")
        put("Notice integrity check failed", "Проверка целостности уведомления не пройдена")
        put("The IPA directory is damaged.", "Каталог IPA повреждён.")
        put("What the step is doing — 45 s", "Что делает текущий шаг — 45 с")
        put("the guest has named no camera", "гость не указал камеру")
        put("warming up (sampling resumed)", "прогрев (выборка возобновлена)")
        put("Cannot read the selected file.", "Не удалось прочитать выбранный файл.")
        put("Checked on Un1box for Android.", "Проверено на Un1box для Android.")
        put("Firmware worker restart failed", "Не удалось перезапустить рабочий процесс прошивки")
        put("Guest audio format unavailable", "Гостевой формат аудио недоступен")
        put("Licenses could not be loaded: ", "Не удалось загрузить лицензии: ")
        put("the camera could not be opened", "не удалось открыть камеру")
        put("Audio output did not initialize", "Аудиовыход не инициализирован")
        put("Choose a device and iOS version", "Выберите устройство и версию iOS")
        put("Could not publish imported file", "Не удалось опубликовать импортированный файл")
        put("Could not publish the download.", "Не удалось сохранить загруженный файл.")
        put("Detailed timing could not start", "Не удалось запустить подробное измерение времени")
        put("I already have the device files", "У меня уже есть файлы устройства")
        put("Not compatible with this device", "Несовместимо с этим устройством")
        put("This file is not a ZIP archive.", "Этот файл не является ZIP-архивом.")
        put("Touch timestamp moved backwards", "Метка времени касания пошла назад")
        put("exceeds the guest package limit", "превышает ограничение размера гостевого пакета")
        put("the guest is on the back camera", "гостевая система использует тыловую камеру")
        put("Could not finish profile capture", "Не удалось завершить запись профиля")
        put("Could not read the frame history", "Не удалось прочитать историю кадров")
        put("Couldn't read this device's apps", "Не удалось прочитать приложения этого устройства")
        put("Display input generation retired", "Поколение ввода дисплея признано устаревшим")
        put("Filtered by the display hardware", "Фильтруется аппаратной частью дисплея")
        put("Nothing here runs on this device", "Здесь нет ничего, что работает на этом устройстве")
        put("Start the device to see its apps", "Запустите устройство, чтобы увидеть его приложения")
        put("Start the guest to install apps.", "Запустите гостевую систему, чтобы устанавливать приложения.")
        put("The app executable is truncated.", "Исполняемый файл приложения усечён.")
        put("The app's Info.plist is damaged.", "Файл Info.plist приложения повреждён.")
        put("The app's executable is missing.", "Исполняемый файл приложения отсутствует.")
        put("Touch timestamp must be positive", "Метка времени касания должна быть положительной")
        put("Versions that run on this device", "Версии, работающие на этом устройстве")
        put("the guest is on the front camera", "гостевая система использует фронтальную камеру")
        put("Application Specific Information:", "Сведения о приложении:")
        put("Couldn't check the Legacy Store: ", "Не удалось проверить классический магазин: ")
        put("Encrypted — cannot open in Un1box", "Зашифровано — нельзя открыть в Un1box")
        put("Invalid executable load commands.", "Недопустимые команды загрузки исполняемого файла.")
        put("Not checked for compatibility yet", "Совместимость ещё не проверена")
        put("Record the next app launch (30 s)", "Записать следующий запуск приложения (30 с)")
        put("Source page could not be opened: ", "Не удалось открыть страницу исходного кода: ")
        put("This copy is no longer available.", "Эта копия больше недоступна.")
        put("Touch timestamp ordering overflow", "Переполнение при упорядочивании меток времени касаний")
        put("Could not create profile directory", "Не удалось создать каталог профиля")
        put("Crisp pixels, then filtered to fit", "Чёткие пиксели, затем фильтрация под размер")
        put("Device worker exited during import", "Рабочий процесс устройства завершился во время импорта")
        put("Fewer pauses when opening iOS apps", "Меньше пауз при открытии приложений iOS")
        put("The app executable is unsupported.", "Исполняемый файл приложения не поддерживается.")
        put("touch dropped: no input generation", "касание отброшено: нет поколения ввода")
        put("A fresh firmware worker is required", "Требуется новый рабочий процесс прошивки")
        put("Could not create a download folder.", "Не удалось создать папку загрузок.")
        put("Couldn't check for newer versions. ", "Не удалось проверить новые версии. ")
        put("Keep Un1box open until it finishes.", "Не закрывайте Un1box до завершения.")
        put("This app has no catalog identifier.", "У этого приложения нет идентификатора каталога.")
        put("skipped (a newer frame replaced it)", "пропущено (заменено более новым кадром)")
        put("——— iOS system log, last launch ———", "——— Системный журнал iOS, последний запуск ———")
        put("Invalid universal executable slices.", "Недопустимые слайсы универсального исполняемого файла.")
        put("Phone volume keys control the device", "Кнопки громкости телефона управляют устройством")
        put("Tap to return; Stop ends this worker", "Нажмите, чтобы вернуться; «Стоп» завершает этот рабочий процесс")
        put("The IPA has a damaged member header.", "В IPA повреждён заголовок элемента.")
        put("Touch timestamp tie budget exhausted", "Исчерпан лимит совпадающих меток времени касаний")
        put("Named after the model and iOS version", "Имя по модели и версии iOS")
        put("This copy belongs to a different app.", "Эта копия относится к другому приложению.")
        put("Could not identify the current iOS app", "Не удалось определить текущее приложение iOS")
        put("Installed. Find it on the Home screen.", "Установлено. Найдите его на главном экране.")
        put("Legacy Store sent an unreadable chart.", "Классический магазин вернул нечитаемый чарт.")
        put("The IPA has a damaged ZIP64 directory.", "В IPA повреждён каталог ZIP64.")
        put("This copy is not made for this device.", "Эта копия не предназначена для этого устройства.")
        put("Touch source timestamp moved backwards", "Метка времени источника касаний пошла назад")
        put("this phone refused the capture session", "телефон отклонил этот сеанс съёмки")
        put("Couldn't read the apps on this device: ", "Не удалось прочитать приложения на этом устройстве: ")
        put("Prepared on a computer, then added here", "Подготовлено на компьютере, затем добавлено сюда")
        put("The download exceeds its expected size.", "Загрузка превышает ожидаемый размер.")
        put("\nPrepared on a computer, then added here", "\nПодготовлено на компьютере, затем добавлено сюда")
        put("A minute or a few, with 1.5 to 5 GB free", "Минута или несколько, при 1,5–5 ГБ свободного места")
        put("Could not stage the built-in guest tools", "Не удалось подготовить встроенные гостевые инструменты")
        put("From an IPSW, or a device image you have", "Из IPSW или из имеющегося у вас образа устройства")
        put("Guest display ownership was not admitted", "Запрос на владение дисплеем гостевой системы отклонён")
        put("Needs hardware this device does not have", "Требуется оборудование, которого нет в этом устройстве")
        put("No active guest frame; gesture cancelled", "Нет активного гостевого кадра; жест отменён")
        put("Select a regular IPA smaller than 2 GiB.", "Выберите обычный IPA размером менее 2 ГиБ.")
        put("unknown (the guest tools did not answer)", "неизвестно (гостевые инструменты не ответили)")
        put("Legacy Store sent an unreadable app list.", "Классический магазин вернул нечитаемый список приложений.")
        put("Legacy Store sent an unreadable response.", "Классический магазин вернул нечитаемый ответ.")
        put("Looking for this app in the Legacy Store…", "Поиск этого приложения в классическом магазине…")
        put("No chart was archived for this selection.", "Для этого выбора чарт не архивирован.")
        put("Profile retained with incomplete metadata", "Профиль сохранён, но метаданные неполные")
        put("The IPA contains an unsafe symbolic link.", "IPA содержит небезопасную символьную ссылку.")
        put("The app's Info.plist is not a dictionary.", "Info.plist приложения не является словарём.")
        put("The guest did not report its device type.", "Гостевая система не сообщила тип своего устройства.")
        put("——— Serial console (guest-serial.log) ———", "——— Последовательная консоль (guest-serial.log) ———")
        put("Legacy Store sent an unreadable app entry.", "Классический магазин вернул нечитаемую запись приложения.")
        put("Start profiling while the guest is running", "Профилирование можно начать, пока гостевая система работает")
        put("Start the device to record its system log.", "Запустите устройство, чтобы записать его системный журнал.")
        put("The IPA directory is too large or damaged.", "Каталог IPA слишком большой или повреждён.")
        put("The archive supplied an invalid file size.", "Архив указал недопустимый размер файла.")
        put("Legacy Store sent an unreadable collection.", "Классический магазин вернул нечитаемую подборку.")
        put("No version of this app runs on this device.", "Ни одна версия этого приложения не работает на этом устройстве.")
        put("Start the device to read its crash reports.", "Запустите устройство, чтобы прочитать его отчёты о сбоях.")
        put("The catalog supplied an unexpected address.", "Каталог вернул неожиданный адрес.")
        put("Debug diagnosis is unavailable in this build", "В этой сборке отладочная диагностика недоступна")
        put("The catalog response exceeds the size limit.", "Ответ каталога превышает ограничение размера.")
        put("The guest stopped. Start it to install apps.", "Гостевая система остановлена. Запустите её, чтобы устанавливать приложения.")
        put("The new device did not appear in the library", "Новое устройство не появилось в библиотеке")
        put("The unpacked IPA exceeds the supported size.", "Распакованный IPA превышает поддерживаемый размер.")
        put("Could not create the preparation tools folder", "Не удалось создать папку инструментов подготовки")
        put("Guest worker unavailable; input was cancelled", "Гостевой рабочий процесс недоступен; ввод отменён")
        put("The archived copy no longer matches this app.", "Архивная копия больше не соответствует этому приложению.")
        put("The device gets no camera from the next start", "Со следующего запуска устройство останется без камеры")
        put("The downloaded IPA failed its checksum check.", "Загруженный IPA не прошёл проверку контрольной суммы.")
        put("The guest returned an incomplete app identity", "Гостевая система вернула неполные данные приложения")
        put("Guest worker unavailable; display was detached", "Гостевой рабочий процесс недоступен; дисплей отключён")
        put("Legacy Store does not support this device yet.", "Классический магазин пока не поддерживает это устройство.")
        put("Legacy Store sent an unreadable category list.", "Классический магазин вернул нечитаемый список категорий.")
        put("The Internet Archive is busy; try again later.", "Internet Archive перегружен, повторите попытку позже.")
        put("The archive supplied an invalid file checksum.", "Архив указал недопустимую контрольную сумму файла.")
        put("The catalog redirected to an insecure address.", "Каталог перенаправил на небезопасный адрес.")
        put("The catalog supplied an invalid HTTPS address.", "Каталог указал недопустимый адрес HTTPS.")
        put("The minimum iOS version could not be verified.", "Не удалось проверить минимальную версию iOS.")
        put("This app is encrypted; select a decrypted IPA.", "Это приложение зашифровано; выберите расшифрованный IPA.")
        put("This archived download is no longer available.", "Эта архивная загрузка больше недоступна.")
        put("A tap when you press Home, Sleep/Wake or volume", "Короткая вибрация при нажатии «Домой», «Сон/пробуждение» или громкости")
        put("App installation is not included in this build.", "В этой сборке установка приложений недоступна.")
        put("Guest worker unavailable; Start a fresh worker.", "Гостевой рабочий процесс недоступен; запустите новый.")
        put("No active guest session; touch was not admitted", "Нет активного гостевого сеанса; касание не принято")
        put("Nobody has tried these yet. They may not start.", "Их ещё никто не пробовал. Они могут не запуститься.")
        put("The IPA uses an unsupported compression method.", "В IPA использован неподдерживаемый метод сжатия.")
        put("Your device is prepared. Open Un1box to add it.", "Устройство подготовлено. Откройте Un1box, чтобы добавить его.")
        put("Include apps that won't run to see every result.", "Включите неработающие приложения, чтобы увидеть все результаты.")
        put("Legacy Store returned a different archived copy.", "Классический магазин вернул другую архивную копию.")
        put("Legacy Store sent an unreadable collection list.", "Классический магазин вернул нечитаемый список подборок.")
        put("This IPA needs an unsupported executable format.", "Для этого IPA нужен неподдерживаемый формат исполняемого файла.")
        put("This app needs a newer processor than the guest.", "Этому приложению нужен процессор новее, чем у гостевой системы.")
        put("Un1box is still starting; try again in a moment.", "Un1box ещё запускается, повторите попытку чуть позже.")
        put("Un1box needs Apple's stock IPSW for this version", "Для этой версии нужен оригинальный IPSW от Apple")
        put("Legacy Store sent unreadable version information.", "Классический магазин вернул нечитаемые сведения о версии.")
        put("The IPA contains a symbolic link outside its app.", "IPA содержит символьную ссылку за пределами приложения.")
        put("The prepared device is kept; try adding it again.", "Подготовленное устройство сохранено; попробуйте добавить его снова.")
        put("This app is not made for the guest device family.", "Это приложение не предназначено для семейства устройств гостевой системы.")
        put("Un1box needs Apple's stock IPSW for this version.", "Для этой версии нужен оригинальный IPSW от Apple.")
        put("Camera unavailable: Android has no camera service.", "Камера недоступна: в Android нет службы камеры.")
        put("Guest app services are not included in this build.", "В этой сборке службы гостевых приложений недоступны.")
        put("The app has invalid bundle or executable metadata.", "У приложения недопустимые метаданные bundle или исполняемого файла.")
        put("This copy has not been analyzed for compatibility.", "Эта копия ещё не проверена на совместимость.")
        put("Guest tools updated; they install at the next start", "Гостевые инструменты обновлены; они установятся при следующем запуске")
        put("The app executable targets an unsupported platform.", "Исполняемый файл приложения нацелен на неподдерживаемую платформу.")
        put("The crash report doesn't say where the app crashed.", "В отчёте о сбое не указано, где именно упало приложение.")
        put("Not shut down cleanly; it recovers at the next start", "Завершение было некорректным; восстановление при следующем запуске")
        put("Stop the running device to switch to or add another.", "Остановите работающее устройство, чтобы переключиться или добавить другое.")
        put("The apps on a device are read from it while it runs.", "Приложения устройства читаются с него, пока оно работает.")
        put("This build of Un1box has no device preparation tools", "В этой сборке Un1box нет инструментов подготовки устройств")
        put("Touch trace selection requires a fresh unused worker", "Для выбора трассировки касаний нужен новый неиспользованный рабочий процесс")
        put("Preparing a device; return to view progress or cancel", "Идёт подготовка устройства; вернитесь, чтобы увидеть прогресс или отменить")
        put("This IPA does not contain a supported ARM executable.", "Этот IPA не содержит поддерживаемого ARM-исполняемого файла.")
        put("Un1box sets up a device for you, right on this phone:", "Un1box подготовит устройство прямо на этом телефоне:")
        put("Camera unavailable: this phone reports no back camera.", "Камера недоступна: телефон сообщает об отсутствии тыловой камеры.")
        put("Reading a device's apps is not included in this build.", "В этой сборке чтение приложений устройства недоступно.")
        put("The first start takes longer while iOS sets itself up.", "Первый запуск занимает больше времени: iOS настраивается.")
        put("Display input outside guest viewport; gesture cancelled", "Ввод дисплея вне области просмотра гостевой системы; жест отменён")
        put("Microphone unavailable: the recorder did not initialize", "Микрофон недоступен: запись не инициализировалась")
        put("The device gets no microphone input from the next start", "Со следующего запуска устройство останется без ввода с микрофона")
        put("The preparation worker stopped unexpectedly. Try again.", "Рабочий процесс подготовки неожиданно остановился. Повторите попытку.")
        put("This copy needs a newer processor than this device has.", "Этой копии нужен процессор новее, чем есть в этом устройстве.")
        put("Select an IPA containing one Payload app and Info.plist.", "Выберите IPA с одним приложением в Payload и файлом Info.plist.")
        put("This phone's cameras feed the device from the next start", "Со следующего запуска устройство будет получать изображение с камер телефона")
        put("Also show it on the drawn device, not only in full screen", "Показывать также на отрисованном устройстве, а не только в полноэкранном режиме")
        put("Stops at a white screen after it's set up. Not fixed yet.", "После настройки останавливается на белом экране. Пока не исправлено.")
        put("Titles in grey haven't been preserved in the archive yet.", "Серые заголовки ещё не сохранены в архиве.")
        put("Graphics provider selection requires a fresh unused worker", "Для выбора графического провайдера нужен новый неиспользованный рабочий процесс")
        put("Previous guest worker has not exited. Retry after it stops.", "Предыдущий гостевой рабочий процесс ещё не завершился. Повторите после его остановки.")
        put("Your phone's motion sensors move the device's accelerometer", "Датчики движения телефона управляют акселерометром устройства")
        put("The IPA contains unsafe, duplicate or encrypted ZIP members.", "IPA содержит небезопасные, повторяющиеся или зашифрованные элементы ZIP.")
        put("The download is incomplete or differs from the archive size.", "Загрузка неполная или её размер не совпадает с размером архива.")
        put("The guest did not answer in time; the result is unconfirmed.", "Гостевая система не ответила вовремя; результат не подтверждён.")
        put("The iPod touch 2G on iOS 3.1.3 is the easiest place to start", "Проще всего начать с iPod touch 2G на iOS 3.1.3")
        put("This phone's microphone feeds the device from the next start", "Со следующего запуска устройство будет получать звук с микрофона телефона")
        put("Guest USB bridge did not create its sockets within 5 seconds.", "Гостевой USB-мост не создал сокеты за 5 секунд.")
        put("These may not start, or may not work fully. Try them and see.", "Они могут не запуститься или работать не полностью. Попробуйте и посмотрите.")
        put("Android stopped the preparation before it finished. Try again.", "Android прервал подготовку до её завершения. Повторите попытку.")
        put("Classic iPod touch and iPhone software, running on your phone.", "Классическое ПО для iPod touch и iPhone — прямо на вашем телефоне.")
        put("Previous firmware worker has not exited. Retry after it stops.", "Предыдущий рабочий процесс прошивки ещё не завершился. Повторите после его остановки.")
        put("The vCPU spent much of this frame waiting for the engine lock.", "Большую часть этого кадра vCPU ждал блокировку движка.")
        put("GL calls took most of this frame: host driver or readback cost.", "Большую часть этого кадра заняли вызовы GL: затраты драйвера хоста или обратного чтения.")
        put("Pick the device to emulate. You'll choose its iOS version next.", "Выберите устройство для эмуляции. Версию iOS выберете следующим шагом.")
        put("Without the camera permission the emulated device has no camera.", "Без разрешения на камеру у эмулируемого устройства не будет камеры.")
        put("Microphone unavailable: this phone refused 44.1 kHz mono capture.", "Микрофон недоступен: телефон отклонил монофоническую запись 44,1 кГц.")
        put("This copy is encrypted or has not been classified as installable.", "Эта копия зашифрована или не признана пригодной для установки.")
        put("This worker is disposing; vCPU accounting requires a fresh worker", "Этот рабочий процесс завершается; для учёта vCPU нужен новый рабочий процесс")
        put("Tested versions are checked on Un1box; the others are yours to try.", "Проверенные версии протестированы в Un1box, остальные можно попробовать самому.")
        put("The guest was busy for the whole frame: more guest work than usual.", "Гостевая система была занята весь кадр: работы больше обычного.")
        put("Without the microphone permission the emulated device has no input.", "Без разрешения на микрофон у эмулируемого устройства не будет ввода.")
        put("This worker is disposing; recovery and Start require a fresh worker.", "Этот рабочий процесс завершается; для восстановления и запуска нужен новый.")
        put("Camera unavailable: this phone offers no YUV size the device can use.", "Камера недоступна: телефон не предоставляет подходящий устройству размер YUV.")
        put("Capture engine timing for the app on screen, for tools/perf/profile.py", "Сбор данных о времени движка для приложения на экране, для tools/perf/profile.py")
        put("This device needs no boot file. Un1box's guest tools are added for you.", "Этому устройству не нужен загрузочный файл. Гостевые инструменты Un1box добавятся сами.")
        put("Apps you install from the Legacy Store or from an IPA file show up here.", "Приложения, установленные из классического магазина или из файла IPA, появятся здесь.")
        put("Required for iPod touch 1G and 2G, and the original iPhone · bootrom.bin", "Нужен для iPod touch 1G и 2G, а также оригинального iPhone · bootrom.bin")
        put("iOS comes from Apple, or the Internet Archive when Apple no longer has it", "iOS берётся у Apple, а если Apple его больше не хранит — из Internet Archive")
        put("As on the real device; Un1box emulates it, so speed depends on your phone.", "Как на настоящем устройстве; Un1box эмулирует его, поэтому скорость зависит от вашего телефона.")
        put("Cancel preparation and wait for a terminal result before closing the worker", "Отмените подготовку и дождитесь окончательного результата, прежде чем закрывать рабочий процесс")
        put("Guest start request was lost; select Start or Recover retained state again.", "Запрос на запуск гостевой системы потерян; снова выберите «Запустить» или «Восстановить сохранённое состояние».")
        put("Without the boot file this device will not start; you can still add it now.", "Без загрузочного файла это устройство не запустится; добавить его можно и сейчас.")
        put("64 KB, part of the chip rather than the IPSW. The same for every iOS version.", "64 КБ, часть чипа, а не IPSW. Одинаков для всех версий iOS.")
        put("The emulator stopped. The device's data is kept; press Start to run it again.", "Эмулятор остановлен. Данные устройства сохранены; нажмите «Запустить», чтобы продолжить.")
        put("Un1box needs Apple's stock IPSW for this version, and the device's boot file.", "Для этой версии нужен оригинальный IPSW от Apple и загрузочный файл устройства.")
        put("Checked on Un1box for Android, but slower than the iPod touch 2G on iOS 3.1.3.", "Проверено в Un1box для Android, но медленнее, чем iPod touch 2G на iOS 3.1.3.")
        put("Its apps, settings and data are removed from this phone. This can't be undone.", "Его приложения, настройки и данные будут удалены с телефона. Отменить это нельзя.")
        put("Starts in Light Touch for Mac's checks; not yet checked on Un1box for Android.", "Запускается в проверках Light Touch for Mac; в Un1box для Android ещё не проверялось.")
        put("This worker is disposing; experimental cache selection requires a fresh worker", "Этот рабочий процесс завершается; для экспериментального выбора кэша нужен новый рабочий процесс")
        put("Start and recovery require a fresh unused guest worker. Stop this worker first.", "Для запуска и восстановления нужен новый неиспользованный гостевой рабочий процесс. Сначала остановите текущий.")
        put("This device also needs its boot file; add it from Settings after it is created.", "Этому устройству также нужен загрузочный файл; добавьте его в настройках после создания.")
        put("Recording the system log for 30 seconds. Open the app now and wait for it to quit.", "Запись системного журнала в течение 30 секунд. Откройте приложение сейчас и дождитесь его выхода.")
        put("Choose a device and iOS version; Un1box downloads Apple's firmware and builds it here", "Выберите устройство и версию iOS; Un1box загрузит прошивку Apple и соберёт её здесь")
        put("Space iOS frees is not given back to your phone yet, so a device's changes only grow.", "Освобождённое iOS место пока не возвращается телефону, поэтому изменения устройства только растут.")
        put("iOS rejected the app's code signature and closed it. This is not an emulation problem.", "iOS отклонил подпись кода приложения и закрыл его. Это не проблема эмуляции.")
        put("Off, they set your phone's volume as usual; the device's own volume buttons are on screen", "В выключенном состоянии они регулируют громкость телефона как обычно; собственные кнопки громкости устройства — на экране")
        put("A button that pauses iOS and opens the frame inspector, to pick apart a hitch you just saw", "Кнопка, которая приостанавливает iOS и открывает инспектор кадров, чтобы разобрать только что увиденный рывок")
        put("Camera off: Un1box has no camera permission. Turn the switch off and on again to grant it.", "Камера выключена: у Un1box нет разрешения на камеру. Выключите и снова включите переключатель, чтобы выдать его.")
        put("The emulator stopped unexpectedly. The device's data is kept; press Start to run it again.", "Эмулятор неожиданно остановился. Данные устройства сохранены; нажмите «Запустить», чтобы продолжить.")
        put("Detailed timing: on (selection.tsv has the per-frame breakdown, events.tsv the raw records)", "Подробное измерение времени: вкл (selection.tsv — разбивка по кадрам, events.tsv — исходные записи)")
        put("The vCPU was runnable but often off-CPU: the phone scheduled other work or slowed the core.", "vCPU был готов к работе, но часто вне CPU: телефон планировал другую работу или снижал частоту ядра.")
        put("Un1box can't build this version on a phone yet. Prepare it on a computer, then add it here.", "Un1box пока не может собрать эту версию на телефоне. Подготовьте её на компьютере и добавьте сюда.")
        put("Search the archive of classic iOS apps. Versions that run on this device are picked for you.", "Поиск по архиву классических приложений iOS. Версии, работающие на этом устройстве, подбираются автоматически.")
        put("Save & Stop keeps everything as it is, open apps included, and picks up right here next time.", "«Сохранить и остановить» сохраняет всё как есть, включая открытые приложения, и в следующий раз продолжит с этого места.")
        put("The guest has not drawn enough frames to inspect. Resume, play for a few seconds and try again.", "Гостевая система нарисовала слишком мало кадров для проверки. Продолжите, поиграйте несколько секунд и повторите.")
        put("Detailed timing: off (turn it on in Settings > Diagnostics for per-frame GL, CPU and lock timing)", "Подробное измерение времени: выкл (включите в «Настройки > Диагностика», чтобы видеть время GL, CPU и блокировок по кадрам)")
        put("Downloads come from Apple's servers when ipsw.me lists them, otherwise from the Internet Archive.", "Загрузки идут с серверов Apple, если они есть на ipsw.me, иначе из Internet Archive.")
        put("The Legacy Store doesn't have this app, so there is no page for it and no newer version to offer.", "В классическом магазине нет этого приложения, поэтому нет ни страницы, ни новой версии.")
        put("Hand-picked by the archive. Whether a version runs on this device is checked when you open an app.", "Отобрано архивом вручную. Работоспособность версии на этом устройстве проверяется при открытии приложения.")
        put("Microphone off: Un1box has no microphone permission. Turn the switch off and on again to grant it.", "Микрофон выключен: у Un1box нет разрешения на микрофон. Выключите и снова включите переключатель, чтобы выдать его.")
        put("iOS shuts down like holding the power button: apps close and the device turns off. Your apps and data are kept.", "iOS выключается так же, как при удержании кнопки питания: приложения закрываются, устройство отключается. Приложения и данные сохраняются.")
        put("iOS closed the app because it took too long to start. The emulator was too slow; the app itself is probably fine.", "iOS закрыл приложение, потому что оно слишком долго запускалось. Эмулятор был слишком медленным; само приложение, скорее всего, в порядке.")
        put("Could not reach Legacy Store. Check your connection, and that Un1box is allowed network access in Android's app settings.", "Не удалось подключиться к классическому магазину. Проверьте соединение и то, что Un1box разрешён доступ к сети в настройках приложений Android.")
        put("Stereo sound, as through the headphone jack; off plays through the device's speaker. Applies the next time the device starts", "Стереозвук, как через разъём для наушников; выключено — звук идёт из динамика устройства. Применяется при следующем запуске устройства")
        put("There is no crash report, so the app didn't crash. Either iOS closed it (for example, to free memory) or it is still running.", "Отчёта о сбое нет, значит приложение не упало. Либо iOS закрыл его (например, чтобы освободить память), либо оно всё ещё работает.")
        put("Records each frame's guest CPU, GL and lock time so the overlay and inspector can explain slow frames. Costs about 6% speed while on.", "Записывает время гостевого CPU, GL и блокировок для каждого кадра, чтобы оверлей и инспектор могли объяснить медленные кадры. Во включённом состоянии снижает скорость примерно на 6%.")
        put("iOS pauses while Un1box is in the background, unless apps are installing. On, it carries on when you come back; off, it waits for you to tap Resume", "iOS приостанавливается, когда Un1box в фоне, кроме случая установки приложений. Включено — работа продолжается при возвращении; выключено — ждёт нажатия «Продолжить»")
        put("On a Mac, Light Touch for Mac's firmwarekit tool prepares it and packs it into an .itbase folder. Copy that folder to this phone, then choose it here:", "На Mac инструмент firmwarekit из Light Touch for Mac подготовит его и упакует в папку .itbase. Скопируйте эту папку на телефон и выберите её здесь:")
        put("Native GLES runs the guest on Android's own GPU driver. ANGLE runs it on the translator this build carries: it can help or hurt, and it applies on the next fresh guest start.", "Нативный GLES запускает гостевую систему на собственном драйвере GPU Android. ANGLE использует встроенный в эту сборку транслятор: он может помочь или навредить; применяется при следующем чистом запуске гостевой системы.")
        put("This build carries no bundled ANGLE provider, so Android's GPU driver is the only choice.", "В эту сборку не входит провайдер ANGLE, поэтому доступен только драйвер GPU Android.")
        put("For a device prepared elsewhere, such as on a computer: its iOS system (.itbase) and, for the iPod touch 1G and 2G and the original iPhone, its boot file.", "Для устройства, подготовленного в другом месте, например на компьютере: его система iOS (.itbase) и, для iPod touch 1G и 2G и оригинального iPhone, загрузочный файл.")
        put("Skips large emulator-code cleanups after app changes. Uses more RAM and may slightly lower steady-state game speed; applies after restarting the emulated iPhone.", "Пропускает масштабную очистку кода эмулятора после смены приложений. Расходует больше ОЗУ и может немного снизить стабильную скорость игр; применяется после перезапуска эмулируемого iPhone.")
        put("Turn on Detailed frame timing in Settings › Diagnostics to see where each frame's time went: guest CPU, GL and lock waits. It costs a few percent of speed while on.", "Включите «Подробное измерение кадров» в «Настройки › Диагностика», чтобы увидеть, на что ушло время каждого кадра: гостевой CPU, GL и ожидание блокировок. Во включённом состоянии теряется несколько процентов скорости.")
        put("The frame copy into the guest's surface runs on a worker thread. Off copies it on the CPU instead, which can be steadier when the guest is waiting most of its frame anyway. Applies at the next guest start.", "Копирование кадра в поверхность гостевой системы выполняется в рабочем потоке. Выключено — копирование идёт на CPU, что может быть стабильнее, когда гостевая система всё равно большую часть кадра ждёт. Применяется при следующем запуске гостевой системы.")
        put("The emulator's log shows glMapBuffer had no device memory to hand back: the app created the buffer with a NULL data pointer, so the emulator returned 0. That is a gap in Un1box's graphics support, not an app-version mismatch.", "Журнал эмулятора показывает, что у glMapBuffer не было памяти устройства для возврата: приложение создало буфер с указателем данных NULL, поэтому эмулятор вернул 0. Это пробел в поддержке графики Un1box, а не несовместимость версии приложения.")
        put("This phone's microphone becomes the device's input, for Voice Memos, Shazam and anything else that records; iOS asks inside the device the first time an app records. Android asks for the microphone permission when you turn this on. Applies the next time the device starts", "Микрофон телефона становится входом устройства — для «Диктофона», Shazam и всего остального, что записывает звук; при первой записи приложения iOS спросит внутри устройства. Android запросит разрешение на микрофон, когда вы это включите. Применяется при следующем запуске устройства")
        put("The frame block models a transfer's full hardware span (3 ms for a full screen), which the guest waits for as emulated time. Off ends a transfer when its pixels are written, which takes that span out of every frame the display pipeline shows. Applies at the next guest start.", "Блок кадра моделирует полную аппаратную длительность передачи (3 мс для полного экрана), и гостевая система ждёт её как эмулируемое время. Выключено — передача завершается, когда записаны её пиксели, что убирает эту длительность из каждого кадра, показываемого конвейером дисплея. Применяется при следующем запуске гостевой системы.")
        put("With headphones, iOS plays games and music in stereo, unchanged.\n\nOn the speaker, iOS reshapes the sound for the device's small built-in speaker: mono, with the bass cut and a limiter to protect it. That processing runs on the emulated CPU, so games run a little slower, and the sound is closer to an old iPod's speaker than to your phone's.\n\nHeadphones also turn off the speaker's protection limiter. Some games count on it to keep loud sounds in check and can sound distorted without it; if a game crackles or clips, switch this off and restart the device.", "С наушниками iOS воспроизводит игры и музыку в стерео, без обработки.\n\nЧерез динамик iOS перестраивает звук под маленький встроенный динамик устройства: моно, с обрезанными низами и ограничителем для его защиты. Эта обработка идёт на эмулируемом CPU, поэтому игры работают немного медленнее, а звук ближе к динамику старого iPod, чем к вашему телефону.\n\nНаушники также отключают защитный ограничитель динамика. Некоторые игры рассчитывают на него, чтобы сдерживать громкие звуки, и без него могут звучать с искажениями; если игра трещит или клиппует, выключите эту опцию и перезапустите устройство.")
        put("Fake call", "Фейковый звонок")
        put("Fake SMS", "Фейковое SMS")
        put("Fake battery", "Фейковая батарея")
        put("Number", "Номер")
        put("Message", "Сообщение")
        put("Ring", "Позвонить")
        put("Hang up", "Завершить")
        put("Send", "Отправить")
        put("Apply", "Применить")
        put("Auto", "Авто")
        put("On", "Вкл")
        put("The phone rings as if this number called. Hang up is the far end ending the call.", "Телефон зазвонит, как будто звонит этот номер; «Завершить» — это дальний абонент кладёт трубку.")
        put("The guest shows the new level at its next battery poll, a few seconds from now.", "Гостевая система покажет новый уровень при следующем опросе батареи, через несколько секунд.")
        put("No packaged graphics provider in this build", "В этой сборке нет встроенного графического провайдера")
        put("The bundled ANGLE provider is not offered in this build", "В этой сборке встроенный провайдер ANGLE не предлагается")
    }

    /** The English match, then the Russian replacement, with $1.. for its groups. */
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("^Preparing runs on this phone, takes (.+) and needs about (.+) free\\. Un1box's guest tools are added for you\\.\$") to "Подготовка выполняется на этом телефоне, занимает \$1 и требует около \$2 свободного места. Гостевые инструменты Un1box добавляются автоматически.",
        Regex("^The app needs (.+), which (.+) doesn't have\\. It was built for a newer iOS than (.+)\\.\$") to "Приложению требуется \$1, которого нет в \$2. Оно собрано для более новой версии iOS, чем \$3.",
        Regex("^The app crashed inside (.+), part of iOS\\. The emulation is the likely cause, not the app\\.\$") to "Приложение упало внутри \$1 — части iOS. Вероятная причина — эмуляция, а не само приложение.",
        Regex("^The app crashed in its own code \\((.+)\\)\\. The emulation can still be the cause if the app relied on something Un1box doesn't support yet\\.\$") to "Приложение упало в собственном коде (\$1). Если приложение полагалось на то, чего Un1box пока не поддерживает, причиной всё ещё может быть эмуляция.",
        Regex("^The emulator's log shows the app called (.+) \\(id (\\d+)\\), a graphics function Un1box doesn't support yet, and got 0 back\\. If the app crashed right after, that is the likely cause\\.\$") to "Журнал эмулятора показывает, что приложение вызвало \$1 (id \$2) — графическую функцию, которую Un1box пока не поддерживает, и получило 0. Если приложение упало сразу после этого, это и есть вероятная причина.",
        Regex("^Found (.+) in (.+)\$") to "\$1 найден в \$2",
        Regex("^(\\S+) s selected\$") to "Выбрано \$1 с",
        Regex("^firmwarekit (.+) failed \\(exit (\\d+)\\)\$") to "firmwarekit \$1 завершился с ошибкой (код выхода \$2)",
        Regex("^Downloading (.+) · (\\d+) of (\\d+)\$") to "Загрузка \$1 · \$2 из \$3",
        Regex("^Installing (.+) · (\\d+) of (\\d+)\$") to "Установка \$1 · \$2 из \$3",
        Regex("^Downloading (.+) of (.+)\$") to "Загрузка \$1 из \$2",
        Regex("^Released (.+) · build (.+)\$") to "Выпущено \$1 · сборка \$2",
        Regex("^(.+) is truncated\$") to "\$1 усечён",
        Regex("^(.+) is not a guest package\$") to "\$1 не является гостевым пакетом",
        Regex("^(.+) exceeds the guest package limit\$") to "\$1 превышает лимит гостевых пакетов",
        Regex("^(.+) requires a fresh unused worker\$") to "\$1 требует нового неиспользуемого рабочего процесса",
        Regex("^Camera: (.+); trying again\$") to "Камера: \$1; повторная попытка",
        Regex("^the camera reported error (.+)\$") to "камера сообщила об ошибке \$1",
        Regex("^touch rejected: (.+)\$") to "касание отклонено: \$1",
        Regex("^gesture cancelled: (.+)\$") to "жест отменён: \$1",
        Regex("^(.+); input release failed: (.+)\$") to "\$1; не удалось освободить ввод: \$2",
        Regex("^Guest audio output stopped \\(code (.+)\\)\\. Stop and restart if it does not recover\\.\$") to "Вывод звука гостевой системы остановлен (код \$1). Если он не восстановится, остановите и запустите заново.",
        Regex("^phone: (.+) frames · (.+) · (.*)\$") to "телефон: \$1 кадров · \$2 · \$3",
        Regex("^Microphone read failed \\((.+)\\)\$") to "Не удалось прочитать с микрофона (\$1)",
        Regex("^The app crashed in (.+), called from (.+), using something that didn't exist\\. On a real device that is a bug in the app; here it can also mean the emulated iOS didn't provide something the app expected\\. The system log below may say which\\.\$") to "Приложение упало в \$1, вызов из \$2, использовано то, чего не существует. На реальном устройстве это ошибка приложения; здесь это также может означать, что эмулируемая iOS не предоставила то, что ожидало приложение. Системный журнал ниже может указать, что именно.",
        Regex("^Reusing the one from (.+)\$") to "Повторно используется экземпляр из \$1",
        Regex("^Last (.+)\$") to "Последние \$1",
        Regex("^(Tested|Tested, slow|Experimental|Untested|Doesn't start yet): (.+)\$") to "\$1: \$2",
        Regex("^Guest start failed: (.+)\\. Writable state and private diagnostics retained\\.\$") to "Не удалось запустить гостевую систему: \$1. Записываемое состояние и приватная диагностика сохранены.",
        Regex("^Couldn't record the system log \\((.+)\\)\\.") to "Не удалось записать системный журнал (\$1).",
        Regex("^(.+) \\((.+)\\)\\. It is the same for every iOS version\\.\$") to "\$1 (\$2). Одинаково для всех версий iOS.",
        Regex("^(.+) graphics provider unavailable: (.+)\\. Restart Un1box to reset the graphics driver\\.\$") to "Графический провайдер \$1 недоступен: \$2. Перезапустите Un1box, чтобы сбросить графический драйвер.",
        Regex("^Download failed: (.+)\$") to "Не удалось загрузить: \$1",
        Regex("^build (.+)\$") to "сборка \$1",
        Regex("^(\\d+) version\$") to "Версий: \$1",
        Regex("^Stop (.+)\\?\$") to "Остановить \$1?",
        Regex("^Delete (.+)\\?\$") to "Удалить \$1?",
        Regex("^Options for (.+)\$") to "Параметры для \$1",
        Regex("^Added (.+); stop the running device to switch to it\$") to "Добавлено \$1; чтобы переключиться на него, остановите работающее устройство",
        Regex("^Added the device, but could not name it \\\"(.+)\\\": (.+)\$") to "Устройство добавлено, но не удалось назвать его «\$1»: \$2",
        Regex("^Added the device, but could not add Un1box's guest tools: (.+)\$") to "Устройство добавлено, но не удалось добавить гостевые инструменты Un1box: \$1",
        Regex("^Added the device, but could not finish setting it up: (.+)\$") to "Устройство добавлено, но не удалось завершить его настройку: \$1",
        Regex("^Installed (.+) apps\$") to "Установлено приложений: \$1",
        Regex("^Couldn't install (.+) apps\$") to "Не удалось установить приложений: \$1",
        Regex("^(.+) installed, (.+) failed\$") to "Установлено: \$1, не удалось: \$2",
        Regex("^Installed (.+)\$") to "Установлено: \$1",
        Regex("^Couldn't install (.+)\$") to "Не удалось установить \$1",
        Regex("^ · (.+) of (.+)\$") to " · \$1 из \$2",
        Regex("^Installing (.+)\$") to "Установка \$1",
        Regex("^Downloading iOS (.+)\$") to "Загрузка iOS \$1",
        Regex("^Downloading from (.+)\$") to "Загрузка из \$1",
        Regex("^Downloading (.+)\$") to "Загрузка \$1",
        Regex("^Downloaded · (.+)\$") to "Загружено · \$1",
        Regex("^Unpacking · (.+)\$") to "Распаковка · \$1",
        Regex("^Copying (.+)\$") to "Копирование \$1",
        Regex("^Adding (.+)\$") to "Добавление \$1",
        Regex("^Preparing (.+)\$") to "Подготовка \$1",
        Regex("^(.+) is ready\$") to "\$1 готово",
        Regex("^(.+) is prepared\$") to "\$1 подготовлено",
        Regex("^Renamed to (.+)\$") to "Переименовано в \$1",
        Regex("^Imported (.+)\$") to "Импортировано: \$1",
        Regex("^(.+) s\$") to "\$1 с",
        Regex("^Profiling (.+)\$") to "Профилирование \$1",
        Regex("^Saved profile: (.+)\$") to "Профиль сохранён: \$1",
        Regex("^Choose iOS for (.+)\$") to "Выберите iOS для \$1",
        Regex("^Tested: (.+)\$") to "Проверено: \$1",
        Regex("^ · (.+) more to try\$") to " · ещё \$1 можно попробовать",
        Regex("^iOS (.+) to (.+)\$") to "iOS от \$1 до \$2",
        Regex("^Show (.+) beta versions\$") to "Показать бета-версий: \$1",
        Regex("^(.+), iOS (.+)\$") to "\$1, iOS \$2",
        Regex("^Boot file · (.+) boot ROM\$") to "Загрузочный файл · boot ROM \$1",
        Regex("^Required · the (.+) boot ROM\$") to "Требуется · boot ROM \$1",
        Regex("^That file is not the (.+) boot ROM\$") to "Этот файл не является boot ROM \$1",
        Regex("^That file is not the iOS (.+) IPSW for the (.+)\$") to "Этот файл не является IPSW iOS \$1 для \$2",
        Regex("^None needed\\. The (.+) starts from the files preparation makes\\.\$") to "Не требуется. \$1 запускается из файлов, созданных при подготовке.",
        Regex("^Downloads come from (.+), and must match the (.+) boot ROM exactly\\.\$") to "Загрузки берутся из \$1 и должны точно соответствовать boot ROM \$2.",
        Regex("^The download from (.+) did not match\$") to "Загрузка из \$1 не совпала",
        Regex("^No download is known for iOS (.+); choose an IPSW you have instead\$") to "Для iOS \$1 неизвестна загрузка; выберите IPSW, который у вас есть",
        Regex("^Could not download iOS (.+) \\((.+)\\)\$") to "Не удалось загрузить iOS \$1 (\$2)",
        Regex("^Could not download the boot file: (.+)\$") to "Не удалось загрузить загрузочный файл: \$1",
        Regex("^Looking up (.+)\$") to "Поиск \$1",
        Regex("^This takes (.+)\\. You can leave Un1box; preparation continues in the background\\.\$") to "Это займёт около \$1. Вы можете выйти из Un1box — подготовка продолжится в фоне.",
        Regex("^Released (.+)\$") to "Выпущено \$1",
        Regex("^It could not be added as a device: (.+)\$") to "Не удалось добавить его как устройство: \$1",
        Regex("^Frame times · (.+) frames\$") to "Время кадров · \$1 кадров",
        Regex("^Frame (\\d+)\$") to "Кадр \$1",
        Regex("^Show all (\\d+)\$") to "Показать все \$1",
        Regex("^at (.+)\$") to "в \$1",
        Regex("^vCPU (.+)% of wall\$") to "vCPU \$1% реального времени",
        Regex("^Serial (.+)\$") to "Серийный номер \$1",
        Regex("^Version (.+) is installed\\. An older version may not read its data\\.\$") to "Установлена версия \$1. Более старая версия может не прочитать его данные.",
        Regex("^Version (.+) is available\$") to "Доступна версия \$1",
        Regex("^Version (.+)\$") to "Версия \$1",
        Regex("^(.+) versions\$") to "Версий: \$1",
        Regex("^(.+) apps\$") to "Приложений: \$1",
        Regex("^Licenses could not be loaded: (.+)\$") to "Не удалось загрузить лицензии: \$1",
        Regex("^License could not be read: (.+)\$") to "Не удалось прочитать лицензию: \$1",
        Regex("^Source page could not be opened: (.+)\$") to "Не удалось открыть страницу с исходным кодом: \$1",
        Regex("^Performance overlay: (.+)\$") to "Оверлей производительности: \$1",
        Regex("^The app needs (.+), which this iOS doesn't include\\.\$") to "Приложению требуется \$1, но эта версия iOS его не содержит.",
        Regex("^Thread (.+):\$") to "Поток \$1:",
        Regex("^Guest app service exited \\((.+)\\) without a result\\. (.+)\$") to "Служба гостевых приложений завершилась (\$1) без результата. \$2",
        Regex("^Legacy Store could not answer the request \\(HTTP (.+)\\)\\.\$") to "Магазин Legacy не смог ответить на запрос (HTTP \$1).",
        Regex("^Legacy Store sent an unreadable (.+)\\.\$") to "Магазин Legacy вернул нечитаемый \$1.",
        Regex("^Start (.+) from its saved data after an unclean shutdown, keeping diagnostics\$") to "Запустить \$1 из сохранённых данных после некорректного завершения, сохранив диагностику",
        Regex("^Install a newer \\.itpack into (.+); it applies at the next start\$") to "Установить более новый .itpack в \$1; он применится при следующем запуске",
        Regex("^Requires iOS (.+); this device runs iOS (.+)\\.\$") to "Требуется iOS \$1; на этом устройстве iOS \$2.",
        Regex("^This app requires iOS (.+); the guest runs (.+)\\.\$") to "Этому приложению требуется iOS \$1; гостевая система работает на \$2.",
        Regex("^Requires iOS (.+)\$") to "Требуется iOS \$1",
        Regex("^iOS (.+) or later\$") to "iOS \$1 или новее",
        Regex("^The app executable requires iOS (.+)\$") to "Исполняемый файл приложения требует iOS \$1",
        Regex("^The IPA member (.+) is truncated\\.\$") to "Элемент IPA \$1 усечён.",
        Regex("^The IPA contains an unsupported or oversized member: (.+)\$") to "IPA содержит неподдерживаемый или слишком большой элемент: \$1",
        Regex("^The IPA is incomplete or damaged: (.+)\$") to "IPA неполон или повреждён: \$1",
        Regex("^Guest tools updated for (.+)\$") to "Гостевые инструменты обновлены для \$1",
        Regex("^Guest start failed: (.+)\$") to "Не удалось запустить гостевую систему: \$1",
        Regex("^Guest worker unavailable: (.+)\\. Writable state retained; Start creates a fresh worker\\.\$") to "Гостевой рабочий процесс недоступен: \$1. Записываемое состояние сохранено; «Запуск» создаст новый рабочий процесс.",
        Regex("^Stop admission failed; writable state retained: (.+)\$") to "Запрос на остановку не удался; записываемое состояние сохранено: \$1",
        Regex("^Couldn't load versions: (.+)\$") to "Не удалось загрузить версии: \$1",
        Regex("^Couldn't read the apps on this device: (.+)\$") to "Не удалось прочитать приложения на этом устройстве: \$1",
        Regex("^Couldn't read crash reports: (.+)\$") to "Не удалось прочитать отчёты о сбоях: \$1",
        Regex("^Couldn't check the Legacy Store: (.+)\$") to "Не удалось проверить магазин Legacy: \$1",
        Regex("^Couldn't check for newer versions\\. (.+)\$") to "Не удалось проверить новые версии. \$1",
        Regex("^Could not connect to Legacy Store: (.+)\$") to "Не удалось подключиться к магазину Legacy: \$1",
        Regex("^Nothing found in (.+)\$") to "В \$1 ничего не найдено",
        Regex("^No prepared iOS system in (.+)\$") to "В \$1 нет подготовленной системы iOS",
        Regex("^Cannot read (.+)\$") to "Не удалось прочитать \$1",
        Regex("^Cannot create (.+)\$") to "Не удалось создать \$1",
        Regex("^Guest app services: (.+)\$") to "Службы гостевых приложений: \$1",
        Regex("^Guest tools not updated: (.+)\$") to "Гостевые инструменты не обновлены: \$1",
        Regex("^Firmware worker unavailable: (.+)\$") to "Рабочий процесс прошивки недоступен: \$1",
        Regex("^Device library unavailable: (.+)\$") to "Библиотека устройств недоступна: \$1",
        Regex("^Could not save: (.+)\$") to "Не удалось сохранить: \$1",
        Regex("^The emulator's log shows it refused (.+), which may have caused the crash\\.\$") to "Журнал эмулятора показывает, что он отклонил \$1, что могло вызвать сбой.",
    )

    private val months = listOf("январь", "февраль", "март", "апрель", "май", "июнь",
        "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")

    /** "March 2010" is a release date in the firmware list; write it the Russian way. */
    private val monthYear = Regex("([A-Z][a-z]+) (\\d{4})")

    fun translate(text: String): String = translate(text, 0)

    private fun translate(text: String, depth: Int): String {
        exact[text]?.let { return localizeDate(it) }
        if (depth < 2) for ((pattern, replacement) in rules)
            if (pattern.containsMatchIn(text)) return localizeDate(expand(pattern, replacement, text, depth))
        return localizeDate(text)
    }

    /** The replacement with its $1.. filled in, each filled part translated in turn. */
    private fun expand(pattern: Regex, replacement: String, text: String, depth: Int): String {
        val match = pattern.find(text) ?: return text
        val out = StringBuilder()
        var i = 0
        while (i < replacement.length) {
            val c = replacement[i]
            if (c == '\\' && i + 1 < replacement.length) { out.append(replacement[i + 1]); i += 2; continue }
            if (c == '$' && i + 1 < replacement.length && replacement[i + 1].isDigit()) {
                val group = match.groupValues.getOrElse(replacement[i + 1] - '0') { "" }
                out.append(translate(group, depth + 1))
                i += 2; continue
            }
            out.append(c); i += 1
        }
        return out.toString()
    }

    private fun localizeDate(text: String): String {
        val match = monthYear.matchEntire(text) ?: return text
        val month = months.indexOf(match.groupValues[1].lowercase())
        if (month < 0) return text
        return "${months[month]} ${match.groupValues[2]} г."
    }
}
