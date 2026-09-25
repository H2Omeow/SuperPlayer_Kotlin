#include <windows.h>

#include <string>
#include <vector>

namespace {

std::wstring executablePath() {
    std::vector<wchar_t> buffer(32768);
    const DWORD length = GetModuleFileNameW(nullptr, buffer.data(), static_cast<DWORD>(buffer.size()));
    if (length == 0 || length >= buffer.size()) {
        return {};
    }
    return std::wstring(buffer.data(), length);
}

std::wstring parentDirectory(const std::wstring& path) {
    const auto separator = path.find_last_of(L"\\/");
    return separator == std::wstring::npos ? std::wstring() : path.substr(0, separator);
}

std::wstring quoteArgument(const std::wstring& argument) {
    std::wstring quoted = L"\"";
    size_t backslashes = 0;
    for (const wchar_t character : argument) {
        if (character == L'\\') {
            ++backslashes;
            continue;
        }
        if (character == L'\"') {
            quoted.append(backslashes * 2 + 1, L'\\');
            quoted.push_back(L'\"');
            backslashes = 0;
            continue;
        }
        quoted.append(backslashes, L'\\');
        backslashes = 0;
        quoted.push_back(character);
    }
    quoted.append(backslashes * 2, L'\\');
    quoted.push_back(L'\"');
    return quoted;
}

int showLaunchError(const wchar_t* message) {
    MessageBoxW(nullptr, message, L"NekoPlayer", MB_OK | MB_ICONERROR);
    return 1;
}

}  // namespace

int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR arguments, int) {
    const std::wstring applicationDirectory = parentDirectory(executablePath());
    if (applicationDirectory.empty()) {
        return showLaunchError(L"无法确定 NekoPlayer 安装目录。");
    }

    const std::wstring java = applicationDirectory + L"\\runtime\\bin\\javaw.exe";
    const std::wstring nativeDirectory = applicationDirectory + L"\\native";
    const std::wstring classpath = applicationDirectory + L"\\lib\\*";
    std::wstring command = quoteArgument(java)
        + L" " + quoteArgument(L"-Dnekoplayer.home=" + applicationDirectory)
        + L" " + quoteArgument(L"-Djava.library.path=" + nativeDirectory)
        + L" -cp " + quoteArgument(classpath)
        + L" top.nekoh2o.player.desktop.MainKt";
    if (arguments != nullptr && *arguments != L'\0') {
        command += L" ";
        command += arguments;
    }

    STARTUPINFOW startup{};
    startup.cb = sizeof(startup);
    PROCESS_INFORMATION process{};
    if (!CreateProcessW(
            java.c_str(), command.data(), nullptr, nullptr, FALSE,
            CREATE_UNICODE_ENVIRONMENT, nullptr, applicationDirectory.c_str(),
            &startup, &process)) {
        return showLaunchError(L"无法启动内置 Java 运行时，请重新下载完整的 Windows 安装包。");
    }

    WaitForSingleObject(process.hProcess, INFINITE);
    DWORD exitCode = 1;
    GetExitCodeProcess(process.hProcess, &exitCode);
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return static_cast<int>(exitCode);
}
