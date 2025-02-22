/*
 * (C) Copyright 2014-2016 mjahnen <jahnen@in.tum.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.github.mjdev.libaums.fs.fat32;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.atech.library.usb.libaums.data.LibAumsException;
import com.github.mjdev.libaums.driver.BlockDeviceDriver;
import com.github.mjdev.libaums.fs.UsbFile;

public class FatFile implements UsbFile {

	private BlockDeviceDriver blockDevice;
	private FAT fat;
	private Fat32BootSector bootSector;

	private FatDirectory parent;
	private ClusterChain chain;
	private FatLfnDirectoryEntry entry;

	/**
	 * Constructs a new file with the given information.
	 * 
	 * @param blockDevice
	 *            The device where the file system is located.
	 * @param fat
	 *            The FAT used to follow cluster chains.
	 * @param bootSector
	 *            The boot sector of the file system.
	 * @param entry
	 *            The corresponding entry in a FAT directory.
	 * @param parent
	 *            The parent directory of the newly constructed file.
	 */
	private FatFile(BlockDeviceDriver blockDevice, FAT fat, Fat32BootSector bootSector,
			FatLfnDirectoryEntry entry, FatDirectory parent) {
		this.blockDevice = blockDevice;
		this.fat = fat;
		this.bootSector = bootSector;
		this.entry = entry;
		this.parent = parent;
	}

	/**
	 * Creates a new file with the given information.
	 * 
	 * @param entry
	 *            The corresponding entry in a FAT directory.
	 * @param blockDevice
	 *            The device where the file system is located.
	 * @param fat
	 *            The FAT used to follow cluster chains.
	 * @param bootSector
	 *            The boot sector of the file system.
	 * @param parent
	 *            The parent directory of the newly created file.
	 * @return The newly constructed file.
	 * @throws IOException
	 *             If reading from device fails.
	 */
	public static FatFile create(FatLfnDirectoryEntry entry, BlockDeviceDriver blockDevice,
			FAT fat, Fat32BootSector bootSector, FatDirectory parent) throws LibAumsException {
		return new FatFile(blockDevice, fat, bootSector, entry, parent);
	}

	/**
	 * Initializes the cluster chain to access the contents of the file.
	 * 
	 * @throws IOException
	 *             If reading from FAT fails.
	 */
	private void initChain() throws LibAumsException {
		if (chain == null) {
			chain = new ClusterChain(entry.getStartCluster(), blockDevice, fat, bootSector);
		}
	}

	@Override
	public UsbFile search(String path) {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public boolean isDirectory() {
		return false;
	}

	@Override
	public String getName() {
		return entry.getName();
	}

	@Override
	public void setName(String newName) throws LibAumsException {
		parent.renameEntry(entry, newName);
	}

	@Override
	public long createdAt() {
		return entry.getActualEntry().getCreatedDateTime();
	}

	@Override
	public long lastModified() {
		return entry.getActualEntry().getLastModifiedDateTime();
	}

	@Override
	public long lastAccessed() {
		return entry.getActualEntry().getLastAccessedDateTime();
	}

	@Override
	public UsbFile getParent() {
		return parent;
	}

	@Override
	public String[] list() {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public UsbFile[] listFiles() throws LibAumsException {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public long getLength() {
		return entry.getFileSize();
	}

	@Override
	public void setLength(long newLength) throws LibAumsException {
        	initChain();
		chain.setLength(newLength);
		entry.setFileSize(newLength);
	}

	@Override
	public void read(long offset, ByteBuffer destination) throws LibAumsException {
		initChain();
		entry.setLastAccessedTimeToNow();
		chain.read(offset, destination);
	}

	@Override
	public void write(long offset, ByteBuffer source) throws LibAumsException {
		initChain();
		long length = offset + source.remaining();
		if (length > getLength())
			setLength(length);
		entry.setLastModifiedTimeToNow();
		chain.write(offset, source);
	}

	@Override
	public void flush() throws LibAumsException {
		// we only have to update the parent because we are always writing
		// everything
		// immediately to the device
		// the parent directory is responsible for updating the
		// FatDirectoryEntry which
		// contains things like the file size and the date time fields
		parent.write();
	}

	@Override
	public void close() throws IOException {
		try {
			flush();
		} catch(LibAumsException ex) {
			throw (IOException) ex.getCause();
		}
	}

	@Override
	public UsbFile createDirectory(String name) throws LibAumsException {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public UsbFile createFile(String name) throws LibAumsException {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public void moveTo(UsbFile destination) throws LibAumsException {
		parent.move(entry, destination);
		parent = (FatDirectory) destination;
	}

	@Override
	public void delete() throws LibAumsException {
		initChain();
		parent.removeEntry(entry);
		parent.write();
		chain.setLength(0);
	}

	@Override
	public boolean isRoot() {
		return false;
	}

}
