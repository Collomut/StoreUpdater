' Launch StockManager silently — no black console window
Set shell = CreateObject("WScript.Shell")
Set fso   = CreateObject("Scripting.FileSystemObject")

' Set working directory to the folder where this script lives
shell.CurrentDirectory = fso.GetParentFolderName(WScript.ScriptFullName)

' Use javaw (windows-mode Java — no console) with IPv4 flag
shell.Run """C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\javaw.exe"" -Djava.net.preferIPv4Stack=true -jar ""target\StockManager-1.0.0.jar""", 0, False
