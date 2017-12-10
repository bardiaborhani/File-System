public class FileSystem {

  private SuperBlock superblock;
  private Directory directory;
  private FileTable filetable;
  
  public FileSytem( int diskBlocks ) { 
  
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
 
  // write directory information from file system to the disk
  // it is written to the root's placement within the disk
  public void sync() {
  
    FileTableEntry rootDir = open( "/" , "w" );
    
    byte[] directoryInBytes = directory.directory2bytes();
    
    write( rootDir , directoryInBytes );
    
    close( rootDir );
    
    superblock.sync();
    
  }
  
  // delete everything and reinstatiate 
  public boolean format( int files ) {
  
    // Cannot format no number of files
    if ( files < 0 ) {
      return false;
    }
    
    superblock.format( files );
    
    directory = new Directory( superblock.inodeBlocks );

    // put the directory in the FileTable
    filetable = new FileTable( directory );
    
    return true;
    
  }
  
  
  // Opens the file specified by the fileName string in 
  // the given mode (where "r" = ready only, "w" = write only, "w+" = read/write, "a" = append).
  public FileTabeEntry open( String filename , String mode ){
  
    //The file is created if it does not exist in the mode "w", "w+" or "a".
    // if the file is not in the directory it means it has not been created
    if( directory.namei( filename ) == -1 ) {
      directory.ialloc( filename );  
    }
   
    FileTableEntry fte = filetable.falloc( filename , mode );
    
    // if the file is opened to write into, deallocate all blocks
    if (  mode == "w" && (!deallocAllBlocks( fte ))  ) {
      return null;
    }
    
    return fte;
    
  }
  
  
  
  // Each close() decreases the count and when the count reaches zerio, the file is no longer
  // in use, and the file's entry is removed from the open-file table
  public boolean synchronized close( FileTableEntry ftEnt ){
  
    if ( ftEnt == null ) {
      return false;
    }
    
    ftEnt.count--;
    
    if ( ftEnt.count == 0 ) { 
      
      // once no threads are using the file - the file is no longer in use and 
      // the fileTableEntry can be deleted
      return filetable.ffree( ftEnt );
      
    }
    
    return true;
    
  }
  
  
  
  
  public int fsize( FileTableEntry ftEnt ){
    
    return ftEnt.inode.length;
    
  }
  
  public int read( FileTableEntry ftEnt, byte[] buffer ){
	int numBytesRead = 0;
	Inode inode = ftEnt.inode;
	if (ftEnt.seek == inode.length)
		return 0;
	int i = 0;
	while (ftEnt.seek <= inode.length && ftEnt.seek <= buffer.length()) {
		short targetBlock = inode.findTargetBlock(ftEnt.seek);
		if (targetBlock == -1)
			return 0;
		byte[] data = new byte[Disk.blockSize];
		SysLib.rawread( indirect , data );
		for (; i < ftEnt.seek <= inode.length && ftEnt.seek <= buffer.length(); i++) {
			if (ftEnt.seek <= inode.length && ftEnt.seek <= buffer.length()){
				buffer[i] = data[i];
				ftEnt.seek+=1;
				numBytesRead++;
			}
		}
	}
  	return numBytesRead;
  }
  
  public int write( FileTableEntry ftEnt, byte[] buffer ){
  
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
  
  
  
  private final int SEEK_SET = 0;
  private final int SEEK_CUR = 1;
  private final int SEEK_END = 2;
  
  
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
