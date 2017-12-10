
public class FileSystem {
	private SuperBlock superblock;
	private Directory directory;
	private FileTable filetable;
	private final int SEEK_SET = 0;
	private final int SEEK_CUR = 1;
	private final int SEEK_END = 2;

	public FileSystem(int diskBlocks) {
		// create superblock, and format disk with inodes in default
		superblock = new SuperBlock( diskBlocks );

		// create directory, and register "/" in directory entry 0
		directory = new Directory( superblock.inodeBlocks );

		// file table is created, and store directory in the file table
		filetable = FileTable( directory );

		// directory reconstruction
		FileTableEntry dirEnt = open( "/" , "r" );

		int dirSize = fsize( dirEnt );

		if ( dirSize > 0 ) {

			byte[] dirData = new byte[dirSize];
			read( dirEnt , dirData );
			directory.bytes2directory( dirData );

		}

		close( dirEnt );
	}

	void sync() {
		FileTableEntry rootDir = this.open("/", "w");
		byte[] directoryInBytes = this.directory.directory2bytes();
		this.write(rootDir, directoryInBytes);
		this.close(rootDir);
		this.superblock.sync();
	}

	boolean format(int files) {
		while(!this.filetable.fempty()) {
			;
		}

		this.superblock.format(files);
		this.directory = new Directory(this.superblock.inodeBlocks);
		this.filetable = new FileTable(this.directory);
		return true;
	}

	FileTableEntry open(String fileName, String mode) {
		FileTableEntry fte = this.filetable.falloc(fileName, mode);
		return mode == "w" && !this.deallocAllBlocks(fte) ? null : fte;
	}

	boolean close(FileTableEntry ftEnt) {
		synchronized(ftEnt) {
			ftEnt.count--;
			if (ftEnt.count > 0) {
				return true;
			}
		}

		return this.filetable.ffree(ftEnt);
	}

	int fsize(FileTableEntry ftEnt) {
		synchronized(ftEnt) {
			return ftEnt.inode.length;
		}
	}

	int read(FileTableEntry ftEnt, byte[] buffer) {
		if (ftEnt.mode != "w" && ftEnt.mode != "a") {
			int dataPointer = 0;
			int dataSize = buffer.length;
			synchronized(ftEnt) {
				while(dataSize > 0 && ftEnt.seekPtr < this.fsize(ftEnt)) {
					int currentBlock = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
					if (currentBlock == -1) {
						break;
					}

					byte[] dataBlock = new byte[512];
					SysLib.rawread(currentBlock, dataBlock);
					int dataOffset = ftEnt.seekPtr % 512;
					int diff = 512 - dataOffset;
					int fileLeft = this.fsize(ftEnt) - ftEnt.seekPtr;
					int var11 = Math.min(Math.min(diff, dataSize), fileLeft);
					System.arraycopy(dataBlock, dataOffset, buffer, dataPointer, var11);
					ftEnt.seekPtr += var11;
					dataPointer += var11;
					dataSize -= var11;
				}

				return dataPointer;
			}
		} else {
			return -1;
		}
	}

	int write(FileTableEntry ftEnt, byte[] buffer) {
		if (ftEnt.mode == "r") {
			return -1;
		} else {
			synchronized(ftEnt) {
				int bytesWritten = 0;
				int bufferSize = buffer.length;

				while(bufferSize > 0) {
					int location = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
					if (location == -1) {
						short newLocation = (short)this.superblock.getFreeBlock();
						switch(ftEnt.inode.registerTargetBlock(ftEnt.seekPtr, newLocation)) {
							case -3:
								short freeBlock = (short)this.superblock.getFreeBlock();
								// indirect pointer is empty
								if (!ftEnt.inode.registerIndexBlock(freeBlock)) {
									SysLib.cerr("ThreadOS: error on write\n");
									return -1;
								}
								// check block pointer error
								if (ftEnt.inode.registerTargetBlock(ftEnt.seekPtr, newLocation) != 0) {
									SysLib.cerr("ThreadOS: error on write\n");
									return -1;
								}
							case 0:
							default:
								location = newLocation;
								break;
							case -2:
							case -1:
								SysLib.cerr("ThreadOS: filesystem error on write\n");
								return -1;
						}
					}

					byte[] tempBuff = new byte[512];
					if (SysLib.rawread(location, tempBuff) == -1) {
						System.exit(2);
					}

					int tempPtr = ftEnt.seekPtr % 512;
					int diff = 512 - tempPtr;
					bufferSize = Math.min(diff, bufferSize);
					System.arraycopy(buffer, bytesWritten, tempBuff, tempPtr, var10);
					SysLib.rawwrite(location, tempBuff);
					ftEnt.seekPtr += var10;
					bytesWritten += var10;
					bufferSize -= var10;
					if (ftEnt.seekPtr > ftEnt.inode.length) {
						ftEnt.inode.length = ftEnt.seekPtr;
					}
				}

				ftEnt.inode.toDisk(ftEnt.iNumber);
				return bytesWritten;
			}
		}
	}
	private boolean deallocAllBlocks( FileTableEntry ftEnt ) {

		// if the inode has file table entries pointing to it then the blocks cannot
		// be deallocated since they are being used by the entries
		if ( ftEnt.inode.count != 1 ) {
			return false;
		}

		// DEALLOCATE THE DIRECT BLOCKS
		for ( int i = 0; i < 11; i++ ) {

			if ( ftEnt.inode.direct[i] != -1 ) {

				superblock.returnBlock(ftEnt.inode.direct[i]);
				ftEnt.inode.direct[i] = -1;

			}

		}

		// DEALLOCATE THE INDIRECT BLOCKS
		short iNodeIndirect = ftEnt.inode.getIndexBlockNumber();

		// if the indirect is pointing to a block of pointers then deallocate them
		if ( indirect > -1 ) {

			// array that will contain the pointers in the block pointed to by indirect
			byte[] indirectPointers = new byte[Disk.blockSize];

			// fill the array above by reading the pointers into the array
			SysLib.rawread( indirect, indirectPointers );

			for (int i = 0; i < Disk.blockSize / 2; i += 2) {

				short block = SysLib.bytes2short( indirectPointers , i );

				if ( block != -1 ) {
					superblock.returnBlock( block );
				}

			}

			// set the indirect variable back to its initial value indicating it isn't pointing to anything
			indirect = -1;

		}


		// read the contents of this file table entry's back to the disk
		ftEnt.inode.toDisk( ftEnt.iNumber );

		// the blocks have successfully been deallocated
		return true;

	}

	// delete the file from the filesystem
	// deletion is done by removing the filename from the directory and removing it from the
	// filesystem using close()
	public synchronized boolean delete( String filename ){

		// grab the inode number to use to remove the file from the directory
		short iNum = dictionary.namei( filename );

		// before deleting it needs to be checked to make sure that no threads are using the file
		// delete the fileEntryPoint if no other threads sharing it
		FileTableEntry ftEnt = open( filename, "r" );
		return  (  close( ftEnt )  &&  dictionary.ifree( iNum )  );

	}
//	private boolean deallocAllBlocks(FileTableEntry var1) {
//		if (var1.inode.count != 1) {
//			return false;
//		} else {
//			byte[] var2 = var1.inode.unregisterIndexBlock();
//			if (var2 != null) {
//				byte var3 = 0;
//
//				short var4;
//				while((var4 = SysLib.bytes2short(var2, var3)) != -1) {
//					this.superblock.returnBlock(var4);
//				}
//			}
//
//			int var5 = 0;
//
//			while(true) {
//				Inode var10001 = var1.inode;
//				if (var5 >= 11) {
//					var1.inode.toDisk(var1.iNumber);
//					return true;
//				}
//
//				if (var1.inode.direct[var5] != -1) {
//					this.superblock.returnBlock(var1.inode.direct[var5]);
//					var1.inode.direct[var5] = -1;
//				}
//
//				++var5;
//			}
//		}
//	}
//
//	boolean delete(String var1) {
//		FileTableEntry var2 = this.open(var1, "w");
//		short var3 = var2.iNumber;
//		return this.close(var2) && this.directory.ifree(var3);
//	}

	int seek(FileTableEntry var1, int var2, int var3) {
		synchronized(var1) {
			switch(var3) {
				case 0:
					if (var2 >= 0 && var2 <= this.fsize(var1)) {
						var1.seekPtr = var2;
						break;
					}

					return -1;
				case 1:
					if (var1.seekPtr + var2 >= 0 && var1.seekPtr + var2 <= this.fsize(var1)) {
						var1.seekPtr += var2;
						break;
					}

					return -1;
				case 2:
					if (this.fsize(var1) + var2 < 0 || this.fsize(var1) + var2 > this.fsize(var1)) {
						return -1;
					}

					var1.seekPtr = this.fsize(var1) + var2;
			}

			return var1.seekPtr;
		}
	}
}
