/*
 * Copyright 2015 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.inmemory.journal

import akka.actor.{ PoisonPill, Props }
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.persistence.PersistentActor
import akka.persistence.inmemory.TestSpec

class JournalTest extends TestSpec {

  case class CounterState(counter: Long) {
    def update(event: String): CounterState = event match {
      case "add" ⇒ copy(counter = counter + 1)
      case "sub" ⇒ copy(counter = counter - 1)
      case "rst" ⇒ copy(counter = 0)
    }
  }

  class Counter(id: Int) extends PersistentActor {
    var state = CounterState(0)

    override val persistenceId: String = "c-" + id

    def updateState(event: String): Unit = {
      state = state.update(event)
    }

    override def receiveCommand: Receive = LoggingReceive {
      case "state" ⇒
        sender() ! state.counter

      case "deleteAll" ⇒
        deleteMessages(Long.MaxValue, permanent = true)

      case event: String ⇒
        persist(event) { (event: String) ⇒
          updateState(event)
        }
    }

    override def receiveRecover: Receive = LoggingReceive {
      case event: String ⇒ state = state.update(event)
    }
  }

  "Counter" should "persist state" in {
    var counter = system.actorOf(Props(new Counter(1)))
    counter ! "add"
    counter ! "add"
    (counter ? "state").futureValue shouldBe 2
    cleanup(counter)
    counter = system.actorOf(Props(new Counter(1)))
    (counter ? "state").futureValue shouldBe 2
    cleanup(counter)
  }

  it should "#1 and #2: delete all journal entries gives 'empty.max' on collection" in {
    var counter = system.actorOf(Props(new Counter(2)))
    counter ! "add"
    counter ! "add"
    (counter ? "state").futureValue shouldBe 2
    cleanup(counter)
    counter = system.actorOf(Props(new Counter(2)))
    (counter ? "state").futureValue shouldBe 2
    counter ! "deleteAll"
    cleanup(counter)
    counter = system.actorOf(Props(new Counter(2)))
    (counter ? "state").futureValue shouldBe 0
    cleanup(counter)
  }
}
