#define UNICODE
#define _UNICODE
#include <windows.h>
#include <shobjidl.h>
#include <shlobj.h>
#include <string>

static void output(const std::wstring& value) {
    int count=WideCharToMultiByte(CP_UTF8,0,value.data(),(int)value.size(),nullptr,0,nullptr,nullptr);
    std::string bytes(count,'\0');
    WideCharToMultiByte(CP_UTF8,0,value.data(),(int)value.size(),bytes.data(),count,nullptr,nullptr);
    DWORD written;WriteFile(GetStdHandle(STD_OUTPUT_HANDLE),bytes.data(),(DWORD)bytes.size(),&written,nullptr);
}
struct Owner {DWORD pid;HWND window=nullptr;};
static BOOL CALLBACK findOwner(HWND window,LPARAM parameter) {
    auto* owner=reinterpret_cast<Owner*>(parameter);DWORD pid=0;GetWindowThreadProcessId(window,&pid);
    if(pid==owner->pid&&IsWindowVisible(window)&&GetWindow(window,GW_OWNER)==nullptr){owner->window=window;return FALSE;}
    return TRUE;
}
int WINAPI wWinMain(HINSTANCE,HINSTANCE,LPWSTR,int) {
    int count=0;LPWSTR* args=CommandLineToArgvW(GetCommandLineW(),&count);
    HRESULT hr=CoInitializeEx(nullptr,COINIT_APARTMENTTHREADED|COINIT_DISABLE_OLE1DDE);
    if(FAILED(hr)){output(L"Could not initialize the Windows dialog.");if(args)LocalFree(args);return 2;}
    IFileOpenDialog* dialog=nullptr;
    hr=CoCreateInstance(CLSID_FileOpenDialog,nullptr,CLSCTX_INPROC_SERVER,IID_PPV_ARGS(&dialog));
    if(FAILED(hr)){output(L"Could not create the Windows file dialog.");CoUninitialize();if(args)LocalFree(args);return 2;}
    int result=2;
    if(count>1&&std::wstring(args[1])==L"--self-test") {output(L"OK");result=0;}
    else {
        FILEOPENDIALOGOPTIONS options;hr=dialog->GetOptions(&options);
        if(SUCCEEDED(hr))hr=dialog->SetOptions(options|FOS_FORCEFILESYSTEM|FOS_FILEMUSTEXIST|FOS_PATHMUSTEXIST|FOS_NOCHANGEDIR|FOS_DONTADDTORECENT);
        dialog->SetTitle(L"Choose a file to transfer");dialog->SetOkButtonLabel(L"Choose file");
        COMDLG_FILTERSPEC filters[]={{L"All files",L"*.*"},{L"Images",L"*.png;*.jpg;*.jpeg;*.gif;*.bmp;*.webp"}};
        dialog->SetFileTypes(2,filters);
        // Keep the OS's last visited folder. Use an explicit folder only as its default.
        IShellItem* folder=nullptr;
        if(count>1&&args[1][0])SHCreateItemFromParsingName(args[1],nullptr,IID_PPV_ARGS(&folder));
        else SHGetKnownFolderItem(FOLDERID_Documents,KF_FLAG_DEFAULT,nullptr,IID_PPV_ARGS(&folder));
        if(folder){dialog->SetDefaultFolder(folder);folder->Release();}
        Owner owner={count>2?static_cast<DWORD>(wcstoul(args[2],nullptr,10)):0};
        if(owner.pid)EnumWindows(findOwner,reinterpret_cast<LPARAM>(&owner));
        if(SUCCEEDED(hr))hr=dialog->Show(owner.window);
        if(hr==HRESULT_FROM_WIN32(ERROR_CANCELLED))result=1;
        else if(SUCCEEDED(hr)) {
            IShellItem* item=nullptr;hr=dialog->GetResult(&item);
            if(SUCCEEDED(hr)) {
                PWSTR path=nullptr;hr=item->GetDisplayName(SIGDN_FILESYSPATH,&path);
                if(SUCCEEDED(hr)){output(path);CoTaskMemFree(path);result=0;}
                item->Release();
            }
        }
        if(result==2)output(L"The Windows file dialog failed (HRESULT "+std::to_wstring(static_cast<long>(hr))+L").");
    }
    dialog->Release();CoUninitialize();if(args)LocalFree(args);return result;
}