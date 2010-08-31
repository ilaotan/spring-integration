/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.stream;

import static org.junit.Assert.assertEquals;

import java.io.StringWriter;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.endpoint.PollingConsumer;
import org.springframework.integration.message.GenericMessage;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author Mark Fisher
 */
public class CharacterStreamWritingMessageHandlerTests {

	private StringWriter writer;

	private CharacterStreamWritingMessageHandler handler;

	private QueueChannel channel;

	private PollingConsumer endpoint;

	private TestTrigger trigger = new TestTrigger();

	private ThreadPoolTaskScheduler scheduler;


	@Before
	public void initialize() {
		writer = new StringWriter();
		handler = new CharacterStreamWritingMessageHandler(writer);
		this.channel = new QueueChannel(10);
		trigger.reset();
		this.endpoint = new PollingConsumer(channel, handler);
		scheduler = new ThreadPoolTaskScheduler();
		this.endpoint.setTaskScheduler(scheduler);
		scheduler.afterPropertiesSet();
		trigger.reset();
		endpoint.setTrigger(trigger);
	}

	@After
	public void stop() throws Exception {
		scheduler.destroy();
	}


	@Test
	public void singleString() {
		handler.handleMessage(new GenericMessage<String>("foo"));
		assertEquals("foo", writer.toString());
	}

	@Test
	public void twoStringsAndNoNewLinesByDefault() {
		endpoint.setMaxMessagesPerPoll(1);
		channel.send(new GenericMessage<String>("foo"), 0);
		channel.send(new GenericMessage<String>("bar"), 0);
		endpoint.start();
		trigger.await();
		endpoint.stop();
		assertEquals("foo", writer.toString());
		trigger.reset();
		endpoint.start();
		trigger.await();
		endpoint.stop();
		assertEquals("foobar", writer.toString());
	}

	@Test
	public void twoStringsWithNewLines() {
		handler.setShouldAppendNewLine(true);
		endpoint.setMaxMessagesPerPoll(1);
		channel.send(new GenericMessage<String>("foo"), 0);
		channel.send(new GenericMessage<String>("bar"), 0);
		endpoint.start();
		trigger.await();
		endpoint.stop();
		String newLine = System.getProperty("line.separator");
		assertEquals("foo" + newLine, writer.toString());
		trigger.reset();
		endpoint.start();
		trigger.await();
		endpoint.stop();
		assertEquals("foo" + newLine + "bar" + newLine, writer.toString());
	}

	@Test
	public void maxMessagesPerTaskSameAsMessageCount() {
		endpoint.setMaxMessagesPerPoll(2);
		channel.send(new GenericMessage<String>("foo"), 0);
		channel.send(new GenericMessage<String>("bar"), 0);
		endpoint.start();
		trigger.await();
		endpoint.stop();
		assertEquals("foobar", writer.toString());
	}

	@Test
	public void maxMessagesPerTaskExceedsMessageCountWithAppendedNewLines() {
		endpoint.setMaxMessagesPerPoll(10);
		endpoint.setReceiveTimeout(0);
		handler.setShouldAppendNewLine(true);
		channel.send(new GenericMessage<String>("foo"), 0);
		channel.send(new GenericMessage<String>("bar"), 0);
		endpoint.start();
		trigger.await();
		endpoint.stop();
		String newLine = System.getProperty("line.separator");
		assertEquals("foo" + newLine + "bar" + newLine, writer.toString());
	}

	@Test
	public void singleNonStringObject() {
		endpoint.setMaxMessagesPerPoll(1);
		TestObject testObject = new TestObject("foo");
		channel.send(new GenericMessage<TestObject>(testObject));
		endpoint.start();
		trigger.await();
		endpoint.stop();
		assertEquals("foo", writer.toString());
	}

	@Test
	public void twoNonStringObjectWithOutNewLines() {
		endpoint.setReceiveTimeout(0);
		endpoint.setMaxMessagesPerPoll(2);
		TestObject testObject1 = new TestObject("foo");
		TestObject testObject2 = new TestObject("bar");
		channel.send(new GenericMessage<TestObject>(testObject1), 0);
		channel.send(new GenericMessage<TestObject>(testObject2), 0);
		endpoint.start();
		trigger.await();
		endpoint.stop();
		assertEquals("foobar", writer.toString());
	}

	@Test
	public void twoNonStringObjectWithNewLines() {
		handler.setShouldAppendNewLine(true);
		endpoint.setReceiveTimeout(0);
		endpoint.setMaxMessagesPerPoll(2);
		TestObject testObject1 = new TestObject("foo");
		TestObject testObject2 = new TestObject("bar");
		channel.send(new GenericMessage<TestObject>(testObject1), 0);
		channel.send(new GenericMessage<TestObject>(testObject2), 0);
		endpoint.start();
		trigger.await();
		endpoint.stop();
		String newLine = System.getProperty("line.separator");
		assertEquals("foo" + newLine + "bar" + newLine, writer.toString());
	}


	private static class TestObject {

		private String text;

		TestObject(String text) {
			this.text = text;
		}

		public String toString() {
			return this.text;
		}
	}


	private static class TestTrigger implements Trigger {

		private final AtomicBoolean hasRun = new AtomicBoolean();

		private volatile CountDownLatch latch = new CountDownLatch(1);

		public Date nextExecutionTime(TriggerContext triggerContext) {
			if (!hasRun.getAndSet(true)) {
				return new Date();
			}
			this.latch.countDown();
			return null;
		}

		public void reset() {
			this.latch = new CountDownLatch(1);
			this.hasRun.set(false);
		}

		public void await() {
			try {
				this.latch.await(1000, TimeUnit.MILLISECONDS);
				if (latch.getCount() != 0) {
					throw new RuntimeException("test timeout");
				}
			}
			catch (InterruptedException e) {
				throw new RuntimeException("test latch.await() interrupted");
			}
		}
	}

}
