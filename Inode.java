public class Inode {
   
   private final static int iNodeSize = 32;       // fix to 32 bytes
   private final static int directSize = 11;      // # direct pointers

   public int length;                             // file size in bytes
   public short count;                            // # file-table entries pointing to this
   public short flag;                             // 0 = unused, 1 = used, ...
   public short direct[] = new short[directSize]; // direct pointers
   public short indirect;                         // a indirect pointer

   Inode( ) {                                     // a default constructor
      length = 0;
      count = 0;
      flag = 1;
      for ( int i = 0; i < directSize; i++ )
         direct[i] = -1;
      indirect = -1;
   }

   
   Inode( short iNumber ) {                       // retrieving inode from disk
      
      int blockNumber = 1 + iNumber / 16;
      byte[] data = new byte[Disk.blockSize];
      SysLib.rawread( blockNumber , data );
      int offset = ( iNumber % 16 ) * 32;
      
      length = SysLib.bytes2int( data , offset );
      offset += 4;
      count = SysLib.bytes2short( data , offset );
      offset += 2;
      flag = SysLib.bytes2short( data , offset );
      offset += 2;
      
      for ( int i = 0; i < directSize; i++ ){
         direct[i] = SysLib.bytes2short( data , offset );
         offset += 2;
      }
         
      indirect = SysLib.bytes2short( data , offset );
      offset += 2;
      
   }

   // put the contents of this Inode object (the field variable values) into disk
   public void toDisk( short iNumber ) {                  // save to disk as the i-th inode
           
      // This byte array will contain all the values of this object (the field variable values)
      // will be used later as a parameter in SysLib.rawwrite() to insert the data into disk
      byte[] iNodeInBytes = new byte[iNodeSize];
      
      // Set the offset variable to index inserted information into the iNodeInBytes array
      int offset = 0;
      
      SysLib.int2bytes( length , iNodeInBytes , offset );
      offset += 4;
      SysLib.short2bytes( count , iNodeInBytes , offset ); 
      offset += 2;
      SysLib.short2bytes( flag , iNodeInBytes , offset );   
      offset += 2;
      
      for ( int i = 0; i < directSize; i++ ){
         SysLib.short2bytes( direct[i] , iNodeInBytes , offset ); 
         offset += 2;
      }
      
      SysLib.short2bytes( indirect , iNodeInBytes , offset ); 
      offset += 2;
      
      // Finds the block in which the inode needs to placed
      int blockNumber = 1 + iNumber / 16;
      
      // Finds the location within the block that the inode needs to be placed
      int diskOffset = ( iNumber % 16 ) * 32;
      
      // ...CHECK IF WORKS WITHOUT CODE BELOW...
      //SysLib.rawwrite( blockNumber +  diskOffset , iNodeInBytes );
      
      byte[] wholeBlock = new byte[Disk.blockSize];
      
      SysLib.rawread( blockNumber , wholeBlock );
      
      // copy the iNode into the block
      // by copying the iNodeInBytes (of size 32) into the wholeBlock representing the block
      // the inode needs to be at a its specific offset in the block  - diskOffset states where the
      // inode needs to sit in the block
      System.arraycopy( iNodeInBytes , 0 , wholeBlock , diskOffset , 32 );
      
      SysLib.rawwrite( blockNumber , wholeBlock );
      
   }
   
   public short getIndexBlockNumber() {
      return indirect;
   }
   
   public boolean setIndexBlock( short indexBlockNumber ) {
      
       // if the index has already been set we cannot change its value
      // the index cant be set if it's already set
      if ( indirect == -1 ) {
         return false;  
      }
     
      // no need to set indirect variable to a block if the 
      // if the file isn't big enough to filled the direct blocks
      for ( int i = 0; i < 11; i++ ) {
         if ( direct == -1 ) {
            return false;
         }
      }
      
      indirect = indexBlockNumber;
      
      byte[] data = new byte[Disk.blockSize];
      
      // fill the index block with block pointers
      // each block pointer is 2 bytes big
      // block size / block pointer size = # of block pointers 
      for ( int offset = 0; offset < Disk.blockSize / 2; offset++ ) { 
         // each pointer is 2 bytes long so a offset of 2 needs happen
         SysLib.short2bytes( (short) -1 , data , offset * 2);
      }
      
      SysLib.rawwrite( indexBlockNumber , data );
      
      return true;
   }
  
   
   public short findTargetBlock( int offset ) {
      
      // specify which data block is to be retrieved 
      target = offset / Disk.blockSize;
      
      // the first 11 data blocks are pointed to directly - if the target is less than 11
      // that means the target is held by one of these data blocks
      if ( target < 11 ) {
         return direct[target];
      }
      
      if ( indirect < 0 ) {
         return -1;
      }
   
      // read the block pointed to by indirect to find the target block because the desired
      // block is not pointed to by the direct pointers
      byte[] data = new byte[Disk.blockSize];
      SysLib.rawread( indirect , data );
      
      // calculate where the target block is within the index block
      return SysLib.bytes2short( data , ( target - 11 ) * 2 );
      
   }
   
   // CONTINUE
   // Write after read and write are done in FileSystem.java
   // dont know if needs 1 or 2 parameters
   public int setTargetBlock( int offset , short block ) {
      
      // set where to place block, specified by the offset
      target = offset / Disk.blockSize;
      
      // set to a direct pointer if one is free and if target specifies that it needs to be
      // held by the direct data blocks
      if ( target < 11 ) {
         
         // first check to make sure nothing is already at the place where the block is to be set
         if (  ) {
            
         }
      }
      
      if (indirect == -1) {
         setIndexBlock(.....);
      }
      
      // if block is not to be set in direct data blocks that means it is 
      // desired to be set by a pointer pointed to by indirect
      
      // if no direct pointers are free then set it to a pointer pointed to by indirect
      
   }
   
}
