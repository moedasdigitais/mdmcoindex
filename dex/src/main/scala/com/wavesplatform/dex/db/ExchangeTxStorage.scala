package com.wavesplatform.dex.db

import com.wavesplatform.dex.domain.transaction.ExchangeTransaction
import com.wavesplatform.dex.domain.bytes.ByteStr
import com.wavesplatform.dex.db.leveldb.{AsyncLevelDB, ReadWriteDB}

import scala.concurrent.Future

trait ExchangeTxStorage {
  def put(tx: ExchangeTransaction): Future[Unit]
}

object ExchangeTxStorage {
  def levelDB(db: AsyncLevelDB): ExchangeTxStorage = new ExchangeTxStorage {
    override def put(tx: ExchangeTransaction): Future[Unit] = db.readWrite { rw =>
      val txKey = DbKeys.exchangeTransaction(tx.id())
      if (!rw.has(txKey)) {
        rw.put(txKey, Some(tx))
        appendTxId(rw, tx.buyOrder.id(), tx.id())
        appendTxId(rw, tx.sellOrder.id(), tx.id())
      }
    }

    private def appendTxId(rw: ReadWriteDB, orderId: ByteStr, txId: ByteStr): Unit = {
      val key = DbKeys.orderTxIdsSeqNr(orderId)
      val nextSeqNr = rw.get(key) + 1
      rw.put(key, nextSeqNr)
      rw.put(DbKeys.orderTxId(orderId, nextSeqNr), txId)
    }
  }
}
