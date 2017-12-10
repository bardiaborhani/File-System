public class SuperBlock {
	private final int defaultInodeBlocks = 64;
	public int totalBlocks;
	public int inodeBlocks;
	public int freeList;

	public SuperBlock(int numBlocks) {
		byte[] var2 = new byte[Disk.blockSize];
		SysLib.rawread(0, var2);
		this.totalBlocks = SysLib.bytes2int(var2, 0);
		this.inodeBlocks = SysLib.bytes2int(var2, 4);
		this.freeList = SysLib.bytes2int(var2, 8);
		if (this.totalBlocks != numBlocks || this.inodeBlocks <= 0 || this.freeList < 2) {
			this.totalBlocks = numBlocks;
			SysLib.cerr("default format( 64 )\n");
			this.format();
		}

	}

	void sync() {
		byte[] tempBlock = new byte[Disk.blockSize];
		SysLib.int2bytes(this.totalBlocks, tempBlock, 0);
		SysLib.int2bytes(this.inodeBlocks, tempBlock, 4);
		SysLib.int2bytes(this.freeList, tempBlock, 8);
		SysLib.rawwrite(0, tempBlock);
		SysLib.cerr("Superblock synchronized\n");
	}

	void format() {
		this.format(64);
	}

	void format(int numFiles) {
		this.inodeBlocks = numFiles;

		for(int i = 0; i < this.inodeBlocks; i++) {
			Inode temp = new Inode();
			temp.flag = 0;
			temp.toDisk(i);
		}

		this.freeList = 2 + this.inodeBlocks * 32 / 512;

		for(int i = this.freeList; i < this.totalBlocks; i++) {
			byte[] newBlock = new byte[Disk.blockSize];

			for(int j = 0; j < Disk.blockSize; j++) {
				newBlock[j] = 0;
			}

			SysLib.int2bytes(i + 1, newBlock, 0);
			SysLib.rawwrite(i, newBlock);
		}

		this.sync();
	}

	public int getFreeBlock() {
		int currentFree = freeList;
		if (currentFree != -1) {
			byte[] newFree = new byte[Disk.blockSize];
			SysLib.rawread(currentFree, newFree);
			this.freeList = SysLib.bytes2int(newFree, 0);
			SysLib.int2bytes(0, newFree, 0);
			SysLib.rawwrite(currentFree, var2);
		}

		return numBlocks;
	}

	public boolean returnBlock(int blockNumber) {
		if (blockNumber < 0) {
			return false;
		} else {
			byte[] newBlock = new byte[Disk.blockSize];

			for(int i = 0; i < Disk.blockSize; i++) {
				newBlock[i] = 0;
			}

			SysLib.int2bytes(this.freeList, newBlock, 0);
			SysLib.rawwrite(blockNumber, newBlock);
			this.freeList = blockNumber;
			return true;
		}
	}
}
