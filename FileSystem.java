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
 
  // FINISH
  void sync() {
  
    superblock.sync();
    
    //directory.directory2bytes();
    
  }
  
  // delete everything and reinstatiate 
  boolean format( int files ) {
  
    superblock.format( files );
    
    directory = new Directory(superblock.inodeBlocks);

    // put the directory in the FileTable
    filetable = new FileTable(directory);
    
    return true;
    
  }
  
  
  
  FileTabeEntry open( String filename , String mode ){
  
    FileTableEntry fte = filetable.falloc( filename , mode );
    return fte;
    
  }
  
  
  
  boolean close( FileTableEntry ftEnt ){
  
    ftEnt.count--;
    
    if ( ftEnt.count == 0 ) { 
      
      return filetable.ffree( ftEnt );
      
    }
    
    return true;
    
  }
  
  
  int fsize( FileTableEntry ftEnt ){
    return ftEnt.inode.length;
  }
  
  
  int read( FileTableEntry ftEnt, byte[] buffer ){
  
  }
  
  int write( FileTableEntry ftEnt, byte[] buffer ){
  
  }
  
  private boolean deallocAllBlocks( FileTableEntry ftEnt ){
  
  }
  
  boolean delete( String filename ){
  
    short iNumber = dictionary.namei(filename);
    return ifree( iNumber );
    
  }
  
  private final int SEEK_SET = 0;
  private final int SEEK_CUR = 1;
  private final int SEEK_END = 2;
  
  int seek( FileTableEntry ftEnt , int offset , int whence ) {
  
  }
 
  
}
