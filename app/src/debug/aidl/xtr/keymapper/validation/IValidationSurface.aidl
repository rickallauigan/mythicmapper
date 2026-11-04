// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.validation;
interface IValidationSurface {
    boolean ready();
    int width();
    int height();
    int activeTouches();
}
