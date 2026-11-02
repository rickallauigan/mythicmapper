// SPDX-License-Identifier: GPL-3.0-or-later
package xtr.keymapper.server;

/** Touch submission only. Mapping, pointer ownership and lifecycle belong to InputService. */
public interface TouchBackend {
    void injectTouch(int androidAction, int logicalPointerId, float pressure, float x, float y);
    boolean hasSubmissionFailure();
}
