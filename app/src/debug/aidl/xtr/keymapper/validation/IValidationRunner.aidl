// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.validation;
import xtr.keymapper.validation.IValidationSurface;
interface IValidationRunner {
    String run(IValidationSurface surface);
    String runHeroes(IValidationSurface surface);
    String screenCleanup(IValidationSurface surface);
}
