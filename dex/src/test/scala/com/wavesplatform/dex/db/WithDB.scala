package com.wavesplatform.dex.db

import java.nio.file.Files

import com.wavesplatform.dex.db.leveldb.LevelDBFactory
import com.wavesplatform.dex.domain.account.Address
import com.wavesplatform.dex.domain.asset.Asset
import com.wavesplatform.dex.util.Implicits._
import com.wavesplatform.dex.util.TestHelpers
import monix.reactive.subjects.Subject
import org.iq80.leveldb.{DB, Options}
import org.scalatest.{BeforeAndAfterEach, Suite}

trait WithDB extends BeforeAndAfterEach { this: Suite =>

  private val path                  = Files.createTempDirectory("lvl").toAbsolutePath
  private var currentDBInstance: DB = _

  def db: DB = currentDBInstance

  protected val ignoreSpendableBalanceChanged: Subject[(Address, Asset), (Address, Asset)] = Subject.empty

  override def beforeEach(): Unit = {
    currentDBInstance = LevelDBFactory.factory.open(path.toFile, new Options().createIfMissing(true))
    super.beforeEach()
  }

  override def afterEach(): Unit =
    try {
      super.afterEach()
      db.close()
    } finally {
      TestHelpers.deleteRecursively(path)
    }

  protected def tempDb(f: DB => Any): Any = {
    val path = Files.createTempDirectory("lvl-temp").toAbsolutePath
    val db   = LevelDBFactory.factory.open(path.toFile, new Options().createIfMissing(true))
    try {
      f(db)
    } finally {
      db.close()
      TestHelpers.deleteRecursively(path)
    }
  }
}
