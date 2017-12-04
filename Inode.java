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
   void toDisk( short iNumber ) {                  // save to disk as the i-th inode
           
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
      
      // ...CHECK...
      SysLib.rawwrite( blockNumber +  diskOffset , iNodeInBytes );
      
   }
   
   short getIndexBlockNumber() {
      
   }
   
   boolean setIndexBlock( short indexBlockNumber ) {
      
   }
  
   short findTargetBlock( ) {
      
   }
   
}
