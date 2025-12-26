package top.expli.bluetoothtester.shizuku;

interface IUserService {
    // Destroy method defined by Shizuku server; must use reserved transaction id
    void destroy() = 16777114;
    // Exit method defined by user
    void exit() = 1;
    // Execute shell command inside user service context and return combined stdout/stderr
    String runShellCommand(String command) = 2;
}
