package org.leng.api;

import org.leng.models.Model;

/**
 * 文案门面：扩展据此取到当前生效的模型，从而让提示语带上服务器选定的人设口吻。
 *
 * <p><b>为什么暴露 {@link Model} 而不是一个 {@code render(key, args)} 方法</b>：
 * {@code Model} 上有上百个<b>强类型</b>方法（{@code getImmunityDenied(target)}、
 * {@code onEscalatedBan(...)}、{@code addWarn(target, reason)} …），每个方法的参数
 * 语义都不同。把它们压成通用的 key-value 渲染会同时丢掉类型安全与可读性，
 * 而收益只是"少一个类型"。
 *
 * <p>取不到模型时 {@link #current()} 返回 {@code null}——调用方必须自行兜底，
 * 而不是假定一定有人设文案。核心侧的兜底是 {@code ConsoleText}。
 */
public interface Messages {

    /** 当前生效的模型；尚未加载或无可用模型时返回 {@code null}。 */
    Model current();

    /** 当前模型名；无模型时返回空串。 */
    String currentName();
}
