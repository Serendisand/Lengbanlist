package org.leng.api;

/**
 * 扩展启用失败。
 *
 * <p>携带一句<b>面向服主</b>的可读原因（例如"需要的契约版本 [3.0,4.0) 与当前的 2.1.6 不兼容"），
 * 而不是给出一串类型名。核心会把它连同扩展 id 打进日志。
 */
public class ExtensionLoadException extends Exception {

    private static final long serialVersionUID = 1L;

    public ExtensionLoadException(String message) {
        super(message);
    }

    public ExtensionLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
