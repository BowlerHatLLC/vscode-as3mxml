/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package avm2.intrinsics.memory
{
	/**
	 * Load 32 bit <code>float</code>.
	 *
	 * <p>
	 * Load a 32 bit (IEEE 754), little-endian, float from global memory
	 * and promote to 64 bit (IEEE 754) double/Number.
	 * </p>
	 *
	 * <p>
	 * The MOPS opcodes all access the backing store of the ByteArray represented
	 * by the current app domain's <code>ApplicationDomain.domainMemory</code> property.
	 * </p>
	 *
	 * <p>
	 * Address ranges for accesses will be range checked using standard comparisons.
	 * No address alignment is necessary.
	 * </p>
	 *
	 * <p>
	 * opcode <b>lf32</b> = <code>56</code> (<code>0x38</code>).
	 * </p>
	 *
	 * @langversion 3.0
	 * @playerversion Flash 11.6
	 * @playerversion AIR 11.6
	 *
	 * @throws RangeError Range check failures will result in an <code>InvalidRangeError</code> exception.
	 */
	public native function lf32(addr:int):Number;
}