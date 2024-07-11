/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package avm2.intrinsics.memory
{
	/**
	 * Store 32 bit <code>integer</code>.
	 *
	 * <p>
	 * Store a 32 bit integer to global memory.
	 * </p>
	 *
	 * <p>
	 * The value is converted to integer using the equivalent of <code>convert_i</code>,
	 * then the 32 bits are stored as four bytes in little-endian order.
	 * </p>
	 *
	 * <p>
	 * The MOPS opcodes all access the backing store of the ByteArray represented
	 * by the current app domain's <code>ApplicationDomain.domainMemory</code> property.
	 * </p>
	 *
	 * <p>
	 * Address ranges for accesses will be range checked using standard comparisons.
	 * </p>
	 *
	 * <p>
	 * opcode <b>si32</b> = <code>60</code> (<code>0x3C</code>).
	 * </p>
	 *
	 * @langversion 3.0
	 * @playerversion Flash 11.6
	 * @playerversion AIR 11.6
	 *
	 * @throws RangeError Range check failures will result in an <code>InvalidRangeError</code> exception.
	 */
	public native function si32(value:int, addr:int):void;
}