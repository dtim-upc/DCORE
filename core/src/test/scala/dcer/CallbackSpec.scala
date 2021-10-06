package dcer

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should._

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future, Promise, blocking}

class CallbackSpec extends AsyncFlatSpec with Matchers {

  behavior of "Callbacks using futures and promises"

  it should "eventually complete" in {
    // NOTE: the global execution context is blocking
    // We suspect it is not setting properly the number of  threads.
    implicit val executionContext: ExecutionContext =
      ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

    val res: AtomicReference[List[Int]] =
      new AtomicReference[List[Int]](List.empty)

    def add(v: Int): Future[Unit] = Future {
      blocking {
        Thread.sleep(1500)
        res.updateAndGet((t: List[Int]) => v +: t)
      }
      ()
    }

    val p = Promise[List[Int]]()

    Future {
      blocking {
        while (res.get.size < 3) {
          Thread.sleep(1000)
        }
        p.success(res.get())
      }
    }

    add(1)
    add(2)
    add(3)

    p.future.map { xs =>
      xs should contain theSameElementsAs List(1, 2, 3)
    }
  }
}
