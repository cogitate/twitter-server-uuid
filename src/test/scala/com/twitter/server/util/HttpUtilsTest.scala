package com.twitter.server.util

import com.twitter.finagle.{Service, http}
import com.twitter.finagle.http.Status
import com.twitter.io.{Buf, Charsets}
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Await
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HttpUtilsTest extends FunSuite {

  test("combine") {
    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
    }

    val muxer0 = new http.HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new http.HttpMuxer().withHandler("/world", world)

    val svc = combine(Seq(muxer0, muxer1))

    val res0 = Await.result(svc(http.Request("/hello")))
    assert(res0.getContent.toString(Charsets.Utf8) === "hello")

    val res1 = Await.result(svc(http.Request("/world")))
    assert(res1.getContent.toString(Charsets.Utf8) === "world")


    val muxer2 = new http.HttpMuxer().withHandler("/hello",
      new Service[Request, Response] {
        def apply(req: Request) = newOk("sup")
      }
    )

    val svcSeq1 = combine(Seq(muxer0, muxer1, muxer2))
    val res2 = Await.result(svcSeq1(http.Request("/hello")))
    assert(res2.getContent.toString(Charsets.Utf8) === "hello")

    val svcSeq2 = combine(Seq(muxer2, muxer0, muxer1))
    val res3 = Await.result(svcSeq2(http.Request("/hello")))
    assert(res3.getContent.toString(Charsets.Utf8) === "sup")

    val res4 = Await.result(svcSeq1(http.Request("/an404")))
    assert(res4.getStatus == Status.NotFound)
  }

  test("isWebBrowser") {
    val req = http.Request("/")

    req.headers().set(
      "User-Agent",
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_4) AppleWebKit/537.36 " +
      "(KHTML, like Gecko) Chrome/37.0.2062.94 Safari/537.36")
    assert(isWebBrowser(req))

    req.headers().set(
      "User-Agent",
      "curl/7.9.8 (i686-pc-linux-gnu) libcurl 7.9.8 (OpenSSL 0.9.6b) (ipv6 enabled)")
    assert(!isWebBrowser(req))
  }

  test("isHtml") {
    // path ends with ".json"
    assert(!isHtml(http.Request("/admin/threads.json")))
    assert(!isHtml(http.Request("/admin/threads.json?k=v")))

    // if not ",json" then defaults to isWebBrowser
    val req = http.Request("/admin/threads?k=v.json")
    req.headers().set(HttpHeaders.Names.USER_AGENT, "Mozilla 5.0/TwitterTest")
    assert(isHtml(req))

    req.headers().set(HttpHeaders.Names.USER_AGENT, "Just/TwitterTest")
    assert(!isHtml(req))
  }

  test("newResponse") {
    val res = Await.result(newResponse(
      version = http.Version.Http11,
      status = http.Status.Ok,
      headers = Seq(("host", "localhost")),
      contentType = "application/json;charset=UTF-8",
      content = Buf.Utf8("[]")
    ))
    assert(res.getProtocolVersion === http.Version.Http11)
    assert(res.getStatus === http.Status.Ok)
    assert(res.headers.get("content-type") === "application/json;charset=UTF-8")
    assert(res.getContent.toString(Charsets.Utf8) === "[]")
  }

  test("newOk") {
    val res = Await.result(newOk("hello"))
    assert(res.getStatus === http.Status.Ok)
    assert(res.headers.get("content-type") === "text/plain;charset=UTF-8")
    assert(res.getContent.toString(Charsets.Utf8) === "hello")
  }

  test("new404") {
    val res = Await.result(new404("not found"))
    assert(res.getStatus === http.Status.NotFound)
    assert(res.headers.get("content-type") === "text/plain;charset=UTF-8")
    assert(res.getContent.toString(Charsets.Utf8) === "not found")
  }

  test("Extract query values") {
    val uri = "http://test.com/testing?foo=bar&baz=qux&hello=world"
    val (_, params) = parse(uri)
    assert(params("foo") === Seq("bar"))
    assert(params.get("fun") === None)
  }

  test("Extract multiple query values") {
    val uri = "http://test.com/testing?foo=bar&baz=qux&foo=world"
    val (_, params) = parse(uri)
    assert(params("foo") === Seq("bar", "world"))
    assert(params("baz") === Seq("qux"))
    assert(params.get("fun") === None)
  }
}
