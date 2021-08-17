package org.spring.study.thread;

import java.util.LinkedHashMap;
import java.util.TreeMap;

class Fork {
	/* 5只筷子，初始为都未被用 */
	private boolean[] used = { false, false, false, false, false, false };

	/* 只有当左右手的筷子都未被使用时，才允许获取筷子，且必须同时获取左右手筷子 */
	public synchronized void takeFork() {
		String name = Thread.currentThread().getName();
		int i = Integer.parseInt(name);
		while (used[i] || used[(i + 1) % 5]) {
			try {
				wait();// 如果左右手有一只正被使用，等待
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		used[i] = true;
		used[(i + 1) % 5] = true;
	}

	/* 必须同时释放左右手的筷子 */
	public synchronized void putFork() {
		String name = Thread.currentThread().getName();
		int i = Integer.parseInt(name);
		used[i] = false;
		used[(i + 1) % 5] = false;
		notifyAll();// 唤醒其他线程
	}
	public static void main(String[] args) {
		LinkedHashMap<String, String>linkedHashMap=new LinkedHashMap<String, String>();
		linkedHashMap.put("1", "111");
		linkedHashMap.put("3", "333");
		linkedHashMap.put("2", "222");
		linkedHashMap.put("4", "444");
		linkedHashMap.put("5", "555");
		for(String str:linkedHashMap.keySet()){
		    System.out.println("key is "+str+" value is "+linkedHashMap.get(str));
		}
	    System.out.println("-------------------------------");

		TreeMap<String, String>map=new TreeMap<String, String>();
	    map.put("1", "1111");
	    map.put("3", "3333");
	    map.put("2", "2222");
	 
	    map.put("4", "4444");
	    map.put("5", "5555");
	    for(String str:map.keySet()){
	        System.out.println("key is "+str+" value is "+map.get(str));
	    }
	}
}