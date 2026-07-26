/**
 * Common mixins. Empty during M0-M2.
 *
 * <p>House rules (see MASTER_PLAN section 6.4):
 * <ul>
 *   <li>{@code @Inject(at = @At("HEAD"), cancellable = true)} is the default form.</li>
 *   <li>{@code @Overwrite} is banned. {@code @Redirect} needs justification in the PR.</li>
 *   <li>Every injection's first statement reads a rule and returns early if it is false.
 *       No unconditional behaviour, ever - that is what makes Phase 2 able to switch it off.</li>
 *   <li>Do not mixin rendering, chunk building or lighting. Sodium and Iris live there.</li>
 * </ul>
 */
package net.unbeta.core.mixin;
