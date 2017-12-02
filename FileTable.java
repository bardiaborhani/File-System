public class FileTable {

   private Vector table;         // the actual entity of this file table
   private Directory dir;        // the root directory 

   public FileTable( Directory directory ) { // constructor
      table = new Vector( );     // instantiate a file (structure) table
      dir = directory;           // receive a reference to the Director
   }                             // from the file system

   // allocate a new file (structure) table entry for this file name
   // allocate/retrieve and register the corresponding inode using dir
   // increment this inode's count
   // immediately write back this inode to the disk
   // return a reference to this file (structure) table entry
   public synchronized FileTableEntry falloc( String filename, String mode ) {
      
      short iNumber = -1;
      Inode inode = null;
      
      while ( true ) {
       
         iNumber = fnames.equals( "/" ) ? : 0 dir.namei( fname );   
         
         if ( iNumber >= 0 ){
          
            inode = new Inode( iNumber );
            
            // was previously "mode.compareTo( "r" )"
            if ( mode.equals( "r" ) ) {
               
               // flag statuses unused (= 0), used(= 1), read(=2), write(=3), delete(=4)
               
               // inode.flag is "read"
               if ( inode.flag == 2 ){
                  // no need to wait
                  break;  
                  
               // inode.flag is "write"  
               } else if ( inode.flag == 3 ) {
                  try{
                     wait();  
                  } catch( InterruptedException e ) { }
               
               // inode.flag is 'to be deleted'   
               } else if ( inode.flag is == 4 ) {
                  iNumber = -1; // no more open
                  return null;
               }
               
            }
            
         }
         
      }
      
      inode.count++;
      inode.toDisk( iNumber );
      
      // create a table entry and register it
      FileTableEntry e = new FileTableEntry( inode , iNumber , mode );
      
      // add the table entry to the vector variable "table" that holds
      // all table entries
      table.addElement( e );  
      
      return e;
      
   }

   public synchronized boolean ffree( FileTableEntry e ) {
      // receive a file table entry reference
      // save the corresponding inode to the disk
      // free this file table entry.
      // return true if this file table entry found in my table
   }

   public synchronized boolean fempty( ) {
      return table.isEmpty( );  // return if table is empty 
   }                            // should be called before starting a format
}
