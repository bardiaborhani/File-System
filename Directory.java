import java.util.Arrays;

public class Directory {
   
   private static int maxChars = 30; // max characters of each file name

   // Directory entries
   private int fsize[];        // each element stores a different file size.
   private char fnames[][];    // each element stores a different file name.

   public Directory( int maxInumber ) { // directory constructor
      fsize = new int[maxInumber];     // maxInumber = max files
      for ( int i = 0; i < maxInumber; i++ ) 
         fsize[i] = 0;                 // all file size initialized to 0
      fnames = new char[maxInumber][maxChars];
      String root = "/";                // entry(inode) 0 is "/"
      fsize[0] = root.length( );        // fsize[0] is the size of "/".
      root.getChars( 0, fsize[0], fnames[0], 0 ); // fnames[0] includes "/"
   }

   // assumes data[] received directory information from disk
   // data[] is the directory file in bytes
   // initializes the Directory instance with this data[]
   public void bytes2directory( byte data[] ) {
      
      int offset = 0;
      
      for ( int i = 0; i < fsize.length; i++, offset += 4 ) {
         fsize[i] = SysLib.bytes2int( data , offset );
      }
      
      for ( int i = 0; i < fnames.length; i++, offset += maxChars * 2 ) { 
       
         String fname = new String( data, offset, maxChars * 2 );
         fname.getChars( 0 , fsize[i] , fnames[i] , 0 );
         
      }
      
   }



   // CHECK
   // converts and return Directory information into a plain byte array
   // this byte array will be written back to disk
   // note: only meaningfull directory information should be converted
   // into bytes.
   public byte[] directory2bytes( ) {
      
      int offset = 0;
      
      int storeFsize = 4 * fsize.length;
      int storeFnames = ( maxChars * 2 ) * fnames.length;
      byte[] dictionaryInBytes = new byte[ storeFsize + storeFnames ];

      for ( int i = 0; i < fsize.length; i++, offset += 4 ) {
         
         SysLib.int2bytes( fsize[i] , dictionaryInBytes , offset );   
         
      }

      // CHECK
      for ( int i = 0; i < fnames.length; i++ ) { 
         
         char[] tempName = fnames[i];
         byte[] nameInBytes = new byte[tempName.length];
         for ( int j = 0; j < tempName.length; j++ ) {
            nameInBytes[j] = (byte) tempName[j];  
	    dictionaryInBytes[offset] = nameInBytes[j]; 
	    offset++;
         }
      }
      
      return dictionaryInBytes;
      
   }



   // converts and return Directory information into a plain byte array
   // this byte array will be written back to disk
   // note: only meaningfull directory information should be converted
   // into bytes.
   /*
   public byte[] directory2bytes( ) {
      
      int offset = 0;
      
      int storeFsize = 4 * fsize.length;
      int storeFnames = ( maxChars * 2 ) * fnames.length;
      byte[] directoryInBytes = new byte[ storeFsize + storeFnames ];

      for ( int i = 0; i < fsize.length; i++, offset += 4 ) {
         
         SysLib.int2bytes( fsize[i] , directoryInBytes , offset );   
         
      }

      // CHECK
      for ( int i = 0; i < fnames.length; i++, offset += maxChars * 2 ) { 
         
         char[] tempName = fnames[i];
         byte[] nameInBytes = new byte[tempName.length];

         for ( int j = 0; j < tempName.length; j++ ) {
            nameInBytes[j] = (byte) tempName[j];   
         }

         //directoryInBytes[offset] = temp;      
      }
      
      return directoryInBytes;
      
   }
   */



   // filename is the one of a file to be created.
   // allocates a new inode number for this filename
   public short ialloc( String filename ) {
      
      // go through all the inode name sizes - if one is empty then there is space for an inode to allocate
      for ( short i = 0; i < fsize.length; i++ ) {
         
         // allocate if space for another inode in the directory
         if( fsize[i] == 0 ){
            
	    // the whole length of the file name cannot be stored if it is more than the maxChars (30) length - have to shrink it - if the size is not too long then the current length can be kept
	    if ( filename.length() > maxChars ) {
            	fsize[i] = maxChars;
	    } else {
	         fsize[i] = filename.length();
	    }

            filename.getChars( 0 , fsize[i] , fnames[i] , 0 );
            return i;
            
         }
         
      }
      
      // indicate that no space to allocate new inode number
      return -1;
      
   }

  


   // deallocates this inumber (inode number)
   // the corresponding file will be deleted.
   public boolean ifree( short iNumber ) {
     
      if ( fsize[iNumber] <= 0 && (iNumber < 0) &&  (iNumber >= fsize.length) ) {
         
         return false;
         
      } else {

         // set file size to 0
         fsize[iNumber] = 0;
         return true;
         
      }
      
   }

   // returns the inumber corresponding to this filename
   public short namei( String filename ) {
      
      // fsize.length == maxInumber
      // loop through all inodes
      for ( short i = 0; i < fsize.length; i++ ) {
	 // convert the name of the file from it's character array form to a string form to get ready to compare with the parameter
	 String fnameString = new String(fnames[i] , 0 , fsize[i]);
	// check and see if the inode's filename matches the filename indicated by the parameter
         if( fnameString.equals(filename) ){
	    // if they match the inode associated with the filename is found and returned
            return i;   
         }
      }
      
      // indicates that the file was not found - it does not exist in the directory	
      return -1;
   }


   
}


