package vacation;

/*
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
 *
 * Unless otherwise noted, the following license applies to STAMP files:
 *
 * Copyright (c) 2007, Stanford University
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *
 *     * Neither the name of Stanford University nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY STANFORD UNIVERSITY ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL STANFORD UNIVERSITY BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

public class Client extends Thread {

  int     id;
  Manager managerPtr;
  Random  randomPtr;
  int     numOperation;
  int     numQueryPerTransaction;
  int     queryRange;
  int     percentUser;

  public Client(final int id, final Manager managerPtr, final int numOperation,
      final int numQueryPerTransaction, final int queryRange,
      final int percentUser) {
    this.randomPtr = new Random(id);
    this.id = id;
    this.managerPtr = managerPtr;
    this.numOperation = numOperation;
    this.numQueryPerTransaction = numQueryPerTransaction;
    this.queryRange = queryRange;
    this.percentUser = percentUser;
  }

  public int selectAction(final int r, final int percentUser) {
    if (r < percentUser) {
      return Defines.ACTION_MAKE_RESERVATION;
    } else if ((r & 1) == 1) {
      return Defines.ACTION_DELETE_CUSTOMER;
    } else {
      return Defines.ACTION_UPDATE_TABLES;
    }
  }

  /**
   * Execute list operations on the database.
   */
  @Override
  public void run() {
    Manager manager = this.managerPtr;
    Random random = this.randomPtr;

    int numOperation = this.numOperation;
    int numQueryPerTransaction = this.numQueryPerTransaction;
    int queryRange = this.queryRange;
    int percentUser = this.percentUser;

    int[] types  = new int[numQueryPerTransaction];
    int[] ids    = new int[numQueryPerTransaction];
    int[] ops    = new int[numQueryPerTransaction];
    int[] prices = new int[numQueryPerTransaction];

    Barrier.enterBarrier();
    for (int i = 0; i < numOperation; i++) {
      int r = random.posrandom_generate() % 100;
      int action = selectAction(r, percentUser);

      if (action == Defines.ACTION_MAKE_RESERVATION) {
        int[] maxPrices = new int[Defines.NUM_RESERVATION_TYPE];
        int[] maxIds = new int[Defines.NUM_RESERVATION_TYPE];
        maxPrices[0] = -1;
        maxPrices[1] = -1;
        maxPrices[2] = -1;
        maxIds[0] = -1;
        maxIds[1] = -1;
        maxIds[2] = -1;
        int n;
        int numQuery = random.posrandom_generate() % numQueryPerTransaction
            + 1;
        int customerId = random.posrandom_generate() % queryRange + 1;
        for (n = 0; n < numQuery; n++) {
          types[n] = random.next() % Defines.NUM_RESERVATION_TYPE;
          ids[n] = (random.next() % queryRange) + 1;
        }
        boolean isFound = false;

        n = atomicMethodOne(manager, types, ids, maxPrices, maxIds, numQuery,
            customerId, isFound);

      } else if (action == Defines.ACTION_DELETE_CUSTOMER) {
        int customerId = random.posrandom_generate() % queryRange + 1;
        atomicMethodTwo(manager, customerId);
      } else if (action == Defines.ACTION_UPDATE_TABLES) {
        int numUpdate = random.posrandom_generate() % numQueryPerTransaction + 1;
        int n;
        for (n = 0; n < numUpdate; n++) {
          types[n] = random.posrandom_generate()
              % Defines.NUM_RESERVATION_TYPE;
          ids[n] = (random.posrandom_generate() % queryRange) + 1;
          ops[n] = random.posrandom_generate() % 2;
          if (ops[n] == 1) {
            prices[n] = ((random.posrandom_generate() % 5) * 10) + 50;
          }
        }
        n = atomicMethodThree(manager, types, ids, ops, prices, numUpdate);
      }
    }
    Barrier.enterBarrier();
  }

//  @Atomic
  private int atomicMethodThree(final Manager managerPtr,
      final int[] types, final int[] ids, final int[] ops, final int[] prices,
      final int numUpdate) {
    int n;
    for (n = 0; n < numUpdate; n++) {
      int t = types[n];
      int id = ids[n];
      int doAdd = ops[n];
      if (doAdd == 1) {
        int newPrice = prices[n];
        if (t == Defines.RESERVATION_CAR) {
          managerPtr.addCar(id, 100, newPrice);
        } else if (t == Defines.RESERVATION_FLIGHT) {
          managerPtr.addFlight(id, 100, newPrice);
        } else if (t == Defines.RESERVATION_ROOM) {
          managerPtr.addRoom(id, 100, newPrice);
        }
      } else { /* do delete */
        if (t == Defines.RESERVATION_CAR) {
          managerPtr.deleteCar(id, 100);
        } else if (t == Defines.RESERVATION_FLIGHT) {
          managerPtr.deleteFlight(id);
        } else if (t == Defines.RESERVATION_ROOM) {
          managerPtr.deleteRoom(id, 100);
        }
      }
    }
    return n;
  }

//  @Atomic
  private void atomicMethodTwo(final Manager managerPtr, final int customerId) {
    int bill = managerPtr.queryCustomerBill(customerId);
    if (bill >= 0) {
      managerPtr.deleteCustomer(customerId);
    }
  }

//  @Atomic
  private int atomicMethodOne(final Manager manager, final int[] types,
      final int[] ids, final int[] maxPrices, final int[] maxIds,
      final int numQuery, final int customerId, boolean isFound) {
    int n;
    for (n = 0; n < numQuery; n++) {
      int t = types[n];
      int id = ids[n];
      int price = -1;
      if (t == Defines.RESERVATION_CAR) {
        if (manager.queryCar(id) >= 0) {
          price = manager.queryCarPrice(id);
        }
      } else if (t == Defines.RESERVATION_FLIGHT) {
        if (manager.queryFlight(id) >= 0) {
          price = manager.queryFlightPrice(id);
        }
      } else if (t == Defines.RESERVATION_ROOM) {
        if (manager.queryRoom(id) >= 0) {
          price = manager.queryRoomPrice(id);
        }
      }
      if (price > maxPrices[t]) {
        maxPrices[t] = price;
        maxIds[t] = id;
        isFound = true;
      }
    } /* for n */
    if (isFound) {
      manager.addCustomer(customerId);
    }
    if (maxIds[Defines.RESERVATION_CAR] > 0) {
      manager.reserveCar(customerId, maxIds[Defines.RESERVATION_CAR]);
    }
    if (maxIds[Defines.RESERVATION_FLIGHT] > 0) {
      manager.reserveFlight(customerId, maxIds[Defines.RESERVATION_FLIGHT]);
    }
    if (maxIds[Defines.RESERVATION_ROOM] > 0) {
      manager.reserveRoom(customerId, maxIds[Defines.RESERVATION_ROOM]);
    }
    return n;
  }
}