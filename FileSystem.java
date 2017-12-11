
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

	public void sync() {
		FileTableEntry rootDir = this.open("/", "w");
		byte[] directoryInBytes = this.directory.directory2bytes();
		this.write(rootDir, directoryInBytes);
		this.close(rootDir);
		this.superblock.sync();
	}

	public boolean format(int files) {
		
		// Cannot format no number of files
		if ( files < 0 ) {
		   return false;
		}
		   
		this.superblock.format(files);
		this.directory = new Directory(this.superblock.inodeBlocks);
		this.filetable = new FileTable(this.directory);
		return true;
	}

	public FileTableEntry open(String fileName, String mode) {
		FileTableEntry fte = this.filetable.falloc(fileName, mode);
		return mode == "w" && !this.deallocAllBlocks(fte) ? null : fte;
	}

	public boolean close(FileTableEntry ftEnt) {
		synchronized(ftEnt) {
			ftEnt.count--;
			if (ftEnt.count > 0) {
				return true;
			}
		}

		return this.filetable.ffree(ftEnt);
	}

	public int fsize(FileTableEntry ftEnt) {
		synchronized(ftEnt) {
			return ftEnt.inode.length;
		}
	}

	public int read(FileTableEntry ftEnt, byte[] buffer) {
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
					int min = Math.min(Math.min(diff, dataSize), fileLeft);
					System.arraycopy(dataBlock, dataOffset, buffer, dataPointer, min);
					ftEnt.seekPtr += min;
					dataPointer += min;
					dataSize -= min;
				}

				return dataPointer;
			}
		} else {
			return -1;
		}
	}

	public int write(FileTableEntry ftEnt, byte[] buffer) {
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
					System.arraycopy(buffer, bytesWritten, tempBuff, tempPtr, bufferSize);
					SysLib.rawwrite(location, tempBuff);
					ftEnt.seekPtr += bufferSize;
					bytesWritten += bufferSize;
					bufferSize -= bufferSize;
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

		// if indirect has an index block then deallocate it
		if (iNodeIndirect >= 0) {

			// array that will contain the pointers in the block pointed to by indirect
               		byte[] indirectPointers = new byte[Disk.blockSize];

			// fill the array above by reading the pointers into the array
                	SysLib.rawread( iNodeIndirect , indirectPointers );

			// set the indirect variable back to its initial value indicating it isn't pointing to anything
            		indirect = -1;
        	}
	
		if ( indirectPointers != null ) {

			for (int offset = 0; offset < Disk.blockSize; offset += 2) {

				// each pointer to a data block is 2 bytes big - so offset goes up by 2 on every loop to cycle
				// through all the pointers in the index block
				short block = SysLib.bytes2short( indirectPointers , offset );

				if ( block != -1 ) {
					superblock.returnBlock( block );
				}

			}

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
	public synchronized int seek( FileTableEntry ftEnt , int offset , int whence ) {


		if ( whence == SEEK_SET ) {

			// If the user attempts to set the seek pointer to a negative number, the pointer must be set to zero.
			if ( offset < 0 ) {

				ftEnt.seekPtr = 0;

			} else if ( offset >= fsize(ftEnt) ) {

				ftEnt.seekPtr = fsize(ftEnt);

			} else {

				ftEnt.seekPtr = offset;

			}

			return ftEnt.seekPtr;

		}




		if ( whence == SEEK_CUR ) {

			// If the user attempts to set the seek pointer to a negative number, the pointer must be set to zero.
			if ( ftEnt.seekPtr + offset < 0 ) {

				ftEnt.seekPtr = 0;

			} else if ( ftEnt.seekPtr + offset >= fsize(ftEnt) ) {

				ftEnt.seekPtr = fsize(ftEnt);

			} else {

				ftEnt.seekPtr += offset;

			}

			return ftEnt.seekPtr;

		}




		if ( whence == SEEK_END ) {

			if ( fsize(ftEnt) + offset < 0 ) {

				ftEnt.seekPtr = 0;

			} else if ( fsize(ftEnt) + offset >= fsize(ftEnt) ) {

				ftEnt.seekPtr = fsize(ftEnt);

			} else {

				ftEnt.seekPtr = fsize(ftEnt) + offset;

			}

			return ftEnt.seekPtr;

		}


	}


}
