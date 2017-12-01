Public class Superblock {
  
  private final int defaultInodeBlocks = 64;
  public int totalBlocks; // the number of disk blocks
  public int totalInodes; // the number of inodes
  public int freeList;    // the block number of the free list's head

  public SuperBlock( int diskSize ) {

    // read the superblock from disk
    byte[] superBlock = new byte[Disk.blockSize]
    SysLib.rawread( 0 , superBlock );
    
    // An int makes up 4 bytes and superblock has three ints (private fields above)
    // the 1st int variable is "totalBlocks"
    totalBlocks = SysLib.bytes2int( superBlock , 0 );
    
    // the 2nd int variable is "totalInodes"
    totalInodes = SysLib.bytes2int( superBlock , 4 );
    
    // the 3rd int variable is "freeList"
    freeList = SysLib.bytes2int( superBlock , 8 );
    
    if ( totalBlocks == diskSize && totalInodes > 0 && freeList >= 2 ) {
    
      // disk contents are valid
      return;
    
    } else {
    
      // need to format disk
      totalBlocks = diskSize;
      format( defaultInodeBlocks );
    
    }
    

  }
   
}
