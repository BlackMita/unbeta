package net.unbeta.core.api;

/**
 * The outcome of resolving one rule, including provenance. Powers {@code /unbeta why}.
 *
 * @param key     the rule
 * @param removed true if Unbeta has removed/disabled this
 * @param source  which layer of the precedence chain produced the winning value
 * @param detail  free text - mod id, config path, or manifest note
 */
public record Resolution(RuleKey key, boolean removed, RuleSource source, String detail) {}
