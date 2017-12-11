import java.util.*;

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
       
	 // the first inode in the directory is the root
         iNumber = filename.equals( "/" ) ? 0: dir.namei( filename );   
         
	 // if the file exists in the directory, then there is no need to create it (no need to allocate an inode for it)
         if ( iNumber >= 0 ){
            
	    // create a local Inode object that represents all the information about the file by grabbing its associated inode
            inode = new Inode( iNumber );
            
            // if the mode is a read , the file needs to not be in the process of being written to so that it can be read
            if ( mode.equals( "r" ) ) {
               
               // flag statuses unused (= 0), used(= 1), read(=2), write(=3), delete(=4)
	       if ( inode.flag == 3) {
                  try{
                     wait();  
                  } catch( InterruptedException e ) { }  
               }
                  
               // inode.flag is 'to be deleted' 
               else if ( inode.flag == 4 ) {
                  iNumber = -1; // no more open
                  return null;
               }

	       // now that the file is being read set its flag to read (2)
	       else {
		  inode.flag = 2;
		  break;
	       }           
            }
	    // if the mode is w , w+, or and the file is already in the directory
            else {
               

               // flag statuses unused (= 0), used(= 1), read(=2), write(=3), delete(=4)

	       if ( inode.flag == 0 || inode.flag == 1) {
                  inode.flag = 2;
		  break; 
               }
	       // inode.flag is 'to be deleted' 
               else if ( inode.flag == 4 ) {
                  iNumber = -1; // no more open
                  return null;
               }
	       try{
                   wait();  
               } catch( InterruptedException e ) { } 
	    }
         }
	 else {
	     // if the file is not in the directory, allocate an inode for it and insert it ito the directory
	     if ( mode.equals("w") || mode.equals("w+")  || mode.equals("a") ) {
		
		iNumber = dir.ialloc(filename);
		if (iNumber == -1)
		    return null;
	        inode = new Inode(iNumber);

		// the file is either being written to
		inode.flag = 3;
		break;
	     } else {
		
	     	return null;
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


   // Remove a file table entry from the file table	  
   // this method is called from the close(FileTableEntry e) method in the FileSystem.java class
   // if a user wants to close its access to a file (doesnt want to write or read to it) will call the close method that will in return call this method to remove the file table entry that is associated with that user and the file being closed
   public synchronized boolean ffree( FileTableEntry e ) {

      // free this file table entry.
	for (int i = 0; i < table.size(); i++)
	{
	    // return true if this file table entry found in my table
	    if (table.get(i) == e) {
		
		// wake up all threads from users who have wanted to write to this inode - since this file table entry is removed, any thread that wanted to write to it now can
		if ( e.inode.flag == 3 ) {
			notifyAll();
		}

		// remove the file table entry from the file table -- main purpose of this method
		table.remove(e);
		
		// removing the file table entry means that there is one less user using the inode - so decrease the inode count (number of users using the file) by 1
		e.inode.count--;

		// reset the inodes flag because at the moment this file table entry is being removed and nothing is affecting the inode
		e.inode.flag = 1;

		// receive a file table entry reference
    		// save the corresponding inode to the disk
		e.inode.toDisk(e.iNumber);
		return true;
	    }	
	
	}

	// file table entry was not in the file table - nothing can be freed
	return false; 
   }


   // return whether or not if the file table has any entries
   public synchronized boolean fempty( ) {
      return table.isEmpty( );  // return if table is empty 
   }                            // should be called before starting a format
}
